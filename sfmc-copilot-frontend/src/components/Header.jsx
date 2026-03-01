import React from 'react';
import './Header.css';

const Header = ({ activeModel, onModelToggle, onToggleSidebar, isConnected }) => {
    return (
        <header className="header glass">
            <div className="header-left">
                <button className="sidebar-toggle" onClick={onToggleSidebar} aria-label="Toggle sidebar">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <line x1="3" y1="6" x2="21" y2="6" />
                        <line x1="3" y1="12" x2="21" y2="12" />
                        <line x1="3" y1="18" x2="21" y2="18" />
                    </svg>
                </button>
                <div className="header-brand">
                    <div className="brand-icon">
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                            <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="url(#grad1)" opacity="0.9" />
                            <path d="M2 17L12 22L22 17" stroke="url(#grad2)" strokeWidth="2" fill="none" />
                            <path d="M2 12L12 17L22 12" stroke="url(#grad2)" strokeWidth="2" fill="none" />
                            <defs>
                                <linearGradient id="grad1" x1="2" y1="2" x2="22" y2="12">
                                    <stop offset="0%" stopColor="#818cf8" />
                                    <stop offset="100%" stopColor="#06b6d4" />
                                </linearGradient>
                                <linearGradient id="grad2" x1="2" y1="12" x2="22" y2="22">
                                    <stop offset="0%" stopColor="#818cf8" />
                                    <stop offset="100%" stopColor="#06b6d4" />
                                </linearGradient>
                            </defs>
                        </svg>
                    </div>
                    <div className="brand-text">
                        <h1 className="brand-name">SFMC <span className="gradient-text">Copilot</span></h1>
                        <span className="brand-subtitle">AI-Powered Marketing Cloud Assistant</span>
                    </div>
                </div>
            </div>

            <div className="header-right">
                <div className="model-switcher">
                    <button
                        className={`model-btn ${activeModel === 'auto' ? 'active' : ''}`}
                        onClick={() => onModelToggle('auto')}
                    >
                        <span className="model-dot auto" />
                        Auto
                    </button>
                    <button
                        className={`model-btn ${activeModel === 'gemini' ? 'active' : ''}`}
                        onClick={() => onModelToggle('gemini')}
                    >
                        <span className="model-dot gemini" />
                        Gemini
                    </button>
                    <button
                        className={`model-btn ${activeModel === 'ollama' ? 'active' : ''}`}
                        onClick={() => onModelToggle('ollama')}
                    >
                        <span className="model-dot ollama" />
                        Ollama
                    </button>
                </div>

                <div className="connection-status">
                    <span className={`status-dot ${isConnected ? 'connected' : 'disconnected'}`} />
                    <span className="status-label">{isConnected ? 'Connected' : 'Disconnected'}</span>
                </div>
            </div>
        </header>
    );
};

export default Header;
