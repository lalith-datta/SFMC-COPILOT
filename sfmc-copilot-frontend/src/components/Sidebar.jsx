import React from 'react';
import './Sidebar.css';

const Sidebar = ({ conversations, activeId, onSelect, onNewChat, isOpen }) => {
    const quickActions = [
        { icon: '📊', label: 'Create Data Extension', prompt: 'Create a data extension' },
        { icon: '✉️', label: 'Create Email', prompt: 'Create an email template' },
        { icon: '⚙️', label: 'Build Automation', prompt: 'Build an automation' },
        { icon: '📈', label: 'Campaign Stats', prompt: 'Show me campaign performance' },
    ];

    return (
        <aside className={`sidebar glass ${isOpen ? 'open' : 'closed'}`}>
            <div className="sidebar-inner">
                {/* New Chat Button */}
                <button className="new-chat-btn" onClick={onNewChat}>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <line x1="12" y1="5" x2="12" y2="19" />
                        <line x1="5" y1="12" x2="19" y2="12" />
                    </svg>
                    New Chat
                </button>

                {/* Quick Actions */}
                <div className="sidebar-section">
                    <h3 className="section-title">Quick Actions</h3>
                    <div className="quick-actions">
                        {quickActions.map((action, idx) => (
                            <button
                                key={idx}
                                className="quick-action-btn"
                                onClick={() => onSelect(null, action.prompt)}
                            >
                                <span className="qa-icon">{action.icon}</span>
                                <span className="qa-label">{action.label}</span>
                            </button>
                        ))}
                    </div>
                </div>

                {/* Conversations */}
                <div className="sidebar-section">
                    <h3 className="section-title">Recent Chats</h3>
                    <div className="conversation-list">
                        {conversations.map((conv) => (
                            <button
                                key={conv.id}
                                className={`conversation-item ${activeId === conv.id ? 'active' : ''}`}
                                onClick={() => onSelect(conv.id)}
                            >
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
                                </svg>
                                <div className="conv-info">
                                    <span className="conv-title">{conv.title}</span>
                                    <span className="conv-time">{conv.time}</span>
                                </div>
                            </button>
                        ))}
                        {conversations.length === 0 && (
                            <div className="empty-conversations">
                                <p>No conversations yet</p>
                                <p className="empty-hint">Start chatting to manage your SFMC</p>
                            </div>
                        )}
                    </div>
                </div>

                {/* Footer */}
                <div className="sidebar-footer">
                    <div className="powered-by">
                        <span>Powered by</span>
                        <span className="gradient-text">Spring AI</span>
                    </div>
                </div>
            </div>
        </aside>
    );
};

export default Sidebar;
