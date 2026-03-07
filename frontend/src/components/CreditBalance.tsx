import React, { useEffect, useState } from 'react'
import { Tooltip, Badge, Spin, message } from 'antd'
import { ThunderboltOutlined, WarningOutlined } from '@ant-design/icons'
import { creditService, UserCreditInfo } from '../services/creditService'
import './CreditBalance.css'

interface CreditBalanceProps {
  showDetails?: boolean
  onBalanceChange?: (balance: number) => void
}

const CreditBalance: React.FC<CreditBalanceProps> = ({ showDetails = false, onBalanceChange }) => {
  const [creditInfo, setCreditInfo] = useState<UserCreditInfo | null>(null)
  const [loading, setLoading] = useState(true)

  const loadBalance = async () => {
    try {
      const info = await creditService.getBalance()
      setCreditInfo(info)
      onBalanceChange?.(info.balance)
    } catch (error) {
      console.error('加载字数点失败:', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadBalance()
    // 每30秒刷新一次
    const interval = setInterval(loadBalance, 30000)
    return () => clearInterval(interval)
  }, [])

  if (loading) {
    return <Spin size="small" />
  }

  if (!creditInfo) {
    return null
  }

  const tooltipContent = (
    <div className="credit-tooltip">
      <div className="credit-tooltip-row">
        <span>可用余额</span>
        <span>{creditInfo.availableBalance.toFixed(2)}</span>
      </div>
      {creditInfo.frozenAmount > 0 && (
        <div className="credit-tooltip-row">
          <span>冻结金额</span>
          <span>{creditInfo.frozenAmount.toFixed(2)}</span>
        </div>
      )}
      <div className="credit-tooltip-divider" />
      <div className="credit-tooltip-row">
        <span>今日消费</span>
        <span>{creditInfo.todayConsumption.toFixed(2)}</span>
      </div>
      <div className="credit-tooltip-row">
        <span>本月消费</span>
        <span>{creditInfo.monthConsumption.toFixed(2)}</span>
      </div>
    </div>
  )

  return (
    <Tooltip title={tooltipContent} placement="bottom">
      <div className={`credit-balance ${creditInfo.lowBalance ? 'low-balance' : ''}`}>
        {creditInfo.lowBalance ? (
          <Badge dot>
            <WarningOutlined className="credit-icon warning" />
          </Badge>
        ) : (
          <ThunderboltOutlined className="credit-icon" />
        )}
        <span className="credit-value">{creditInfo.balance.toFixed(2)}</span>
        <span className="credit-label">字数点</span>
      </div>
    </Tooltip>
  )
}

export default CreditBalance
