import React from 'react'
import { Spin } from 'antd'
import './LoadingSpinner.css'

interface LoadingSpinnerProps {
  size?: 'small' | 'default' | 'large'
  tip?: string
  spinning?: boolean
  children?: React.ReactNode
  overlay?: boolean
}

const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  size = 'default',
  tip = '加载中...',
  spinning = true,
  children,
  overlay = false
}) => {
  const customIndicator = (
    <div className={`custom-spinner ${size}`}>
      <div className="spinner-ring">
        <div className="spinner-circle"></div>
        <div className="spinner-circle"></div>
        <div className="spinner-circle"></div>
        <div className="spinner-circle"></div>
      </div>
    </div>
  )

  if (overlay) {
    return (
      <div className="loading-overlay">
        <div className="loading-content">
          {customIndicator}
          {tip && <div className="loading-tip">{tip}</div>}
        </div>
      </div>
    )
  }

  if (children) {
    return (
      <Spin 
        spinning={spinning} 
        indicator={customIndicator}
        tip={tip}
        size={size}
      >
        {children}
      </Spin>
    )
  }

  return (
    <div className="loading-container">
      {customIndicator}
      {tip && <div className="loading-tip">{tip}</div>}
    </div>
  )
}

export default LoadingSpinner
