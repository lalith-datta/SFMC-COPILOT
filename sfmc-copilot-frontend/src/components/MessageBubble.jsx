import React from 'react';
import './MessageBubble.css';

const MessageBubble = ({ message }) => {
    const isUser = message.role === 'user';

    // Simple markdown-like rendering for bold, tables, and line breaks
    const renderContent = (text) => {
        // Split into lines
        const lines = text.split('\n');
        const elements = [];
        let tableBuffer = [];
        let inTable = false;
        let key = 0;

        const processLine = (line) => {
            // Bold text
            line = line.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>');
            // Inline code
            line = line.replace(/`(.*?)`/g, '<code>$1</code>');
            // Italic
            line = line.replace(/\*(.*?)\*/g, '<em>$1</em>');
            return line;
        };

        for (let i = 0; i < lines.length; i++) {
            const line = lines[i].trim();

            // Detect table rows
            if (line.startsWith('|') && line.endsWith('|')) {
                if (!inTable) {
                    inTable = true;
                    tableBuffer = [];
                }
                // Skip separator rows
                if (!/^\|[\s\-:|]+\|$/.test(line)) {
                    tableBuffer.push(line);
                }
                continue;
            }

            // End table
            if (inTable) {
                inTable = false;
                elements.push(renderTable(tableBuffer, key++));
                tableBuffer = [];
            }

            // Empty line
            if (!line) {
                elements.push(<div key={key++} className="msg-spacer" />);
                continue;
            }

            // List items
            if (/^\d+\.\s/.test(line)) {
                elements.push(
                    <div
                        key={key++}
                        className="msg-list-item numbered"
                        dangerouslySetInnerHTML={{ __html: processLine(line) }}
                    />
                );
                continue;
            }

            if (line.startsWith('- ') || line.startsWith('• ')) {
                elements.push(
                    <div
                        key={key++}
                        className="msg-list-item"
                        dangerouslySetInnerHTML={{ __html: processLine(line.slice(2)) }}
                    />
                );
                continue;
            }

            // Regular paragraph
            elements.push(
                <p
                    key={key++}
                    className="msg-paragraph"
                    dangerouslySetInnerHTML={{ __html: processLine(line) }}
                />
            );
        }

        // Handle remaining table
        if (inTable && tableBuffer.length > 0) {
            elements.push(renderTable(tableBuffer, key++));
        }

        return elements;
    };

    const renderTable = (rows, key) => {
        if (rows.length === 0) return null;
        const parseRow = (row) =>
            row.split('|').filter((cell) => cell.trim() !== '').map((cell) => cell.trim());

        const headers = parseRow(rows[0]);
        const body = rows.slice(1).map(parseRow);

        return (
            <div key={key} className="msg-table-wrapper">
                <table className="msg-table">
                    <thead>
                        <tr>
                            {headers.map((h, i) => (
                                <th key={i} dangerouslySetInnerHTML={{ __html: h.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>') }} />
                            ))}
                        </tr>
                    </thead>
                    <tbody>
                        {body.map((row, i) => (
                            <tr key={i}>
                                {row.map((cell, j) => (
                                    <td key={j} dangerouslySetInnerHTML={{ __html: cell.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>') }} />
                                ))}
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        );
    };

    return (
        <div className={`message-bubble-wrapper ${isUser ? 'user' : 'ai'}`}>
            {!isUser && (
                <div className="avatar ai-avatar">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                        <path d="M12 2L2 7L12 12L22 7L12 2Z" fill="#818cf8" opacity="0.9" />
                        <path d="M2 17L12 22L22 17" stroke="#06b6d4" strokeWidth="2" fill="none" />
                        <path d="M2 12L12 17L22 12" stroke="#818cf8" strokeWidth="2" fill="none" />
                    </svg>
                </div>
            )}

            <div className={`message-bubble ${isUser ? 'user-bubble' : 'ai-bubble'}`}>
                <div className="message-content">
                    {renderContent(message.text)}
                </div>

                {/* Action confirmation card */}
                {message.action && message.action.status === 'pending_confirmation' && (
                    <div className="action-card">
                        <div className="action-header">
                            <span className="action-icon">⚡</span>
                            <span className="action-title">Action Required</span>
                        </div>
                        <p className="action-desc">
                            {message.action.type.replace(/_/g, ' ')} → <strong>{message.action.name}</strong>
                        </p>
                        <div className="action-buttons">
                            <button
                                className="action-btn confirm"
                                onClick={() => message.onConfirm && message.onConfirm(true)}
                            >
                                ✓ Execute
                            </button>
                            <button
                                className="action-btn cancel"
                                onClick={() => message.onConfirm && message.onConfirm(false)}
                            >
                                ✕ Cancel
                            </button>
                        </div>
                    </div>
                )}

                {/* Footer: model badge + timestamp */}
                <div className="message-footer">
                    {!isUser && message.model && (
                        <span className={`model-badge ${message.model === 'gemini' ? 'gemini' : 'ollama'}`}>
                            {message.model === 'gemini' ? '✨ Gemini' : '🦙 Ollama'}
                        </span>
                    )}
                    <span className="message-time">{message.time}</span>
                </div>
            </div>

            {isUser && (
                <div className="avatar user-avatar">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                        <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                        <circle cx="12" cy="7" r="4" />
                    </svg>
                </div>
            )}
        </div>
    );
};

export default MessageBubble;
