import React, { useState, useCallback, useEffect } from 'react';
import Header from './components/Header';
import Sidebar from './components/Sidebar';
import ChatWindow from './components/ChatWindow';
import ChatInput from './components/ChatInput';
import { sendMessage, confirmAction, checkHealth } from './services/api';
import './App.css';

function App() {
  const [messages, setMessages] = useState([]);
  const [isTyping, setIsTyping] = useState(false);
  const [activeModel, setActiveModel] = useState('auto');
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [conversations, setConversations] = useState([]);
  const [activeConvId, setActiveConvId] = useState(null);
  const [isConnected, setIsConnected] = useState(false);

  // Check backend health on mount and periodically
  useEffect(() => {
    const check = async () => {
      const result = await checkHealth();
      setIsConnected(result.status === 'UP');
    };
    check();
    const interval = setInterval(check, 30000); // Check every 30s
    return () => clearInterval(interval);
  }, []);

  const formatTime = () => {
    return new Date().toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    });
  };

  const handleSend = useCallback(async (text) => {
    // Add user message
    const userMsg = {
      id: Date.now(),
      role: 'user',
      text,
      time: formatTime(),
    };

    setMessages((prev) => [...prev, userMsg]);
    setIsTyping(true);

    try {
      // Pass activeModel (auto/gemini/ollama) to the backend
      const response = await sendMessage(text, activeConvId || 'default', activeModel);

      // Handle action confirmation callback
      const onConfirm = response.action
        ? async (confirmed) => {
          setIsTyping(true);
          const result = await confirmAction(response.action.name, confirmed);
          const confirmMsg = {
            id: Date.now() + 2,
            role: 'assistant',
            text: result.text,
            model: result.model,
            time: formatTime(),
            action: null,
          };
          setMessages((prev) => [...prev, confirmMsg]);
          setIsTyping(false);
        }
        : null;

      const aiMsg = {
        id: Date.now() + 1,
        role: 'assistant',
        text: response.text,
        model: response.model,
        time: formatTime(),
        action: response.action,
        onConfirm,
      };

      setMessages((prev) => [...prev, aiMsg]);

      // Update conversation list
      if (conversations.length === 0 || !activeConvId) {
        const newConv = {
          id: Date.now(),
          title: text.length > 35 ? text.slice(0, 35) + '...' : text,
          time: formatTime(),
        };
        setConversations((prev) => [newConv, ...prev]);
        setActiveConvId(newConv.id);
      }
    } catch (error) {
      const errorMsg = {
        id: Date.now() + 1,
        role: 'assistant',
        text: '❌ Sorry, I encountered an error connecting to the AI service. Please ensure the backend is running on port 8080 and try again.\n\n**Error:** ' + error.message,
        model: 'system',
        time: formatTime(),
        action: null,
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setIsTyping(false);
    }
  }, [conversations, activeConvId, activeModel]);

  const handleNewChat = useCallback(() => {
    setMessages([]);
    setActiveConvId(null);
  }, []);

  const handleSelectConversation = useCallback((convId, prompt) => {
    if (prompt) {
      // Quick action clicked — send the prompt
      handleSend(prompt);
    } else if (convId) {
      setActiveConvId(convId);
    }
  }, [handleSend]);

  const handleModelToggle = useCallback((model) => {
    setActiveModel(model);
  }, []);

  return (
    <div className="app-layout">
      <Header
        activeModel={activeModel}
        onModelToggle={handleModelToggle}
        onToggleSidebar={() => setSidebarOpen((o) => !o)}
        isConnected={isConnected}
      />
      <div className="app-body">
        <Sidebar
          conversations={conversations}
          activeId={activeConvId}
          onSelect={handleSelectConversation}
          onNewChat={handleNewChat}
          isOpen={sidebarOpen}
        />
        <main className="chat-area">
          <ChatWindow messages={messages} isTyping={isTyping} />
          <ChatInput onSend={handleSend} disabled={isTyping} />
        </main>
      </div>
    </div>
  );
}

export default App;
