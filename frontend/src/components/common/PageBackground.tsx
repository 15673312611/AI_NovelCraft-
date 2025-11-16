import React from 'react'
import './PageBackground.css'

/**
 * 页面装饰性背景组件
 * 添加微妙的视觉元素提升页面美感
 */
const PageBackground: React.FC = () => {
  return (
    <div className="page-background-wrapper">
      {/* 网格图案 */}
      <div className="bg-grid-pattern"></div>
      
      {/* 渐变光晕 */}
      <div className="bg-gradient-orbs">
        <div className="gradient-orb orb-1"></div>
        <div className="gradient-orb orb-2"></div>
        <div className="gradient-orb orb-3"></div>
      </div>

      {/* 浮动粒子（可选） */}
      <div className="bg-particles">
        {Array.from({ length: 12 }).map((_, i) => (
          <div
            key={i}
            className="particle"
            style={{
              left: `${Math.random() * 100}%`,
              top: `${Math.random() * 100}%`,
              animationDelay: `${Math.random() * 10}s`,
              animationDuration: `${15 + Math.random() * 10}s`,
            }}
          />
        ))}
      </div>
    </div>
  )
}

export default PageBackground

