import React from 'react';
import './TypingIndicator.css';

const TypingIndicator = () => {
    return (
        <div className="typing-wrapper">
            <div className="avatar ai-avatar typing-avatar">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                    <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="#818cf8" opacity="0.9" />
                    <path d="M2 17L12 22L22 17" stroke="#06b6d4" strokeWidth="2" fill="none" />
                    <path d="M2 12L12 17L22 12" stroke="#818cf8" strokeWidth="2" fill="none" />
                </svg>
            </div>
            <div className="typing-bubble">
                <div className="typing-dots">
                    <span className="typing-dot" />
                    <span className="typing-dot" />
                    <span className="typing-dot" />
                </div>
                <span className="typing-label">SFMC Copilot is thinking...</span>
            </div>
        </div>
    );
};

export default TypingIndicator;
