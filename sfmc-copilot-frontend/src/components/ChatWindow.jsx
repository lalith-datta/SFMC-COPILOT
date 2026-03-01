import React, { useRef, useEffect } from 'react';
import MessageBubble from './MessageBubble';
import TypingIndicator from './TypingIndicator';
import './ChatWindow.css';

const ChatWindow = ({ messages, isTyping }) => {
    const bottomRef = useRef(null);

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isTyping]);

    return (
        <div className="chat-window">
            {messages.length === 0 ? (
                <div className="welcome-screen">
                    <div className="welcome-icon">
                        <svg width="56" height="56" viewBox="0 0 24 24" fill="none">
                            <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="url(#wg1)" opacity="0.9" />
                            <path d="M2 17L12 22L22 17" stroke="url(#wg2)" strokeWidth="2" fill="none" />
                            <path d="M2 12L12 17L22 12" stroke="url(#wg2)" strokeWidth="2" fill="none" />
                            <defs>
                                <linearGradient id="wg1" x1="2" y1="2" x2="22" y2="12">
                                    <stop offset="0%" stopColor="#818cf8" />
                                    <stop offset="100%" stopColor="#06b6d4" />
                                </linearGradient>
                                <linearGradient id="wg2" x1="2" y1="12" x2="22" y2="22">
                                    <stop offset="0%" stopColor="#818cf8" />
                                    <stop offset="100%" stopColor="#06b6d4" />
                                </linearGradient>
                            </defs>
                        </svg>
                    </div>
                    <h2 className="welcome-title">
                        Welcome to <span className="gradient-text">SFMC Copilot</span>
                    </h2>
                    <p className="welcome-subtitle">
                        Your AI-powered assistant for Salesforce Marketing Cloud.
                        Ask me to create data extensions, build automations, send emails, and more.
                    </p>
                    <div className="welcome-suggestions">
                        
                        <div className="suggestion-grid">
                            
                            <div className="suggestion-card" >
                                <span className="suggestion-icon">📊</span>
                                <span className="suggestion-text">Create a Data Extension with custom fields</span>
                            </div>
                            
                            
                            <div className="suggestion-card">
                                <span className="suggestion-icon">✉️</span>
                                <span className="suggestion-text">Build an email template for a campaign</span>
                            </div>
                            <div className="suggestion-card">
                                <span className="suggestion-icon">⚙️</span>
                                <span className="suggestion-text">Set up a scheduled automation</span>
                            </div>
                            <div className="suggestion-card">
                                <span className="suggestion-icon">📈</span>
                                <span className="suggestion-text">Show campaign performance metrics</span>
                            </div>
                        </div>
                    </div>
                </div>
            ) : (
                <div className="messages-container">
                    {messages.map((msg) => (
                        <MessageBubble key={msg.id} message={msg} />
                    ))}
                    {isTyping && <TypingIndicator />}
                    <div ref={bottomRef} />
                </div>
            )}
        </div>
    );
};

export default ChatWindow;
