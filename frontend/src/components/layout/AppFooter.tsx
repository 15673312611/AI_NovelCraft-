import React from 'react'
import { Layout } from 'antd'
import './AppFooter.css'

const { Footer } = Layout

const AppFooter: React.FC = () => {
  return (
    <Footer className="app-footer">
      <div className="footer-content">
        <div className="footer-brand">
          <h3>小说创作系统</h3>
          <p>AI 辅助创作，让灵感持续不断。规划—构思—撰写—润色，一站式完成。</p>
        </div>

        <div className="footer-links">
          <a className="footer-link" href="#/novels">我的小说</a>
          <a className="footer-link" href="#/dashboard">仪表盘</a>
          <a className="footer-link" href="#/prompts">提示词库</a>
          <a className="footer-link" href="#/world-view-builder">世界观</a>
          <a className="footer-link" href="#/settings">设置</a>
        </div>

        <div className="footer-social">
          <div className="social-icon">🐦</div>
          <div className="social-icon">💬</div>
          <div className="social-icon">⭐</div>
        </div>

        <div className="footer-divider" />

        <div className="footer-bottom">
          <p className="footer-copyright">© 2025 小说创作系统 · Made with <span className="heart">♥</span></p>
          <div className="footer-info">
            <span>版本 v1.0.0</span>
            <span>支持：云端同步 · 多端访问</span>
          </div>
        </div>
      </div>
    </Footer>
  )
}

export default AppFooter 