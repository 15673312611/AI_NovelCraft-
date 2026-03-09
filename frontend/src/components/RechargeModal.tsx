import React, { useEffect, useRef, useState } from 'react'
import { Modal, Button, Tag, message, Spin, Alert } from 'antd'
import { CheckCircleOutlined } from '@ant-design/icons'
import { creditService, CreditPackage, RechargeConfig, RechargeOrder, RechargePayType } from '@/services/creditService'
import './RechargeModal.css'

interface RechargeModalProps {
  visible: boolean
  onCancel: () => void
  onSuccess?: () => void
}

const payTypeLabel: Record<RechargePayType, string> = {
  alipay: '支付宝',
  wxpay: '微信支付',
  qqpay: 'QQ钱包',
  cashier: '收银台',
}

const RechargeModal: React.FC<RechargeModalProps> = ({ visible, onCancel, onSuccess }) => {
  const [packages, setPackages] = useState<CreditPackage[]>([])
  const [config, setConfig] = useState<RechargeConfig | null>(null)
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [checking, setChecking] = useState(false)
  const [selectedPackageId, setSelectedPackageId] = useState<number | null>(null)
  const [payType, setPayType] = useState<RechargePayType>('alipay')
  const [currentOrder, setCurrentOrder] = useState<RechargeOrder | null>(null)
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    if (visible) {
      loadData()
    } else {
      clearPolling()
      setCurrentOrder(null)
      setSubmitting(false)
      setChecking(false)
    }
    return () => clearPolling()
  }, [visible])

  const clearPolling = () => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current)
      pollTimerRef.current = null
    }
  }

  const loadData = async () => {
    setLoading(true)
    try {
      const [list, rechargeConfig] = await Promise.all([
        creditService.getPackages(),
        creditService.getRechargeConfig(),
      ])
      setPackages(list)
      setConfig(rechargeConfig)

      if (list.length > 0) {
        setSelectedPackageId(prev => prev ?? list[0].id)
      }

      const availableTypes = rechargeConfig?.supportedPayTypes || []
      if (availableTypes.length > 0) {
        if (!availableTypes.includes(payType)) {
          setPayType(rechargeConfig.defaultPayType || availableTypes[0])
        }
      }
    } catch (error: any) {
      message.error(error?.message || '加载充值信息失败')
    } finally {
      setLoading(false)
    }
  }

  const getStatusText = (status?: RechargeOrder['status']) => {
    if (status === 'PAID') return '已支付'
    if (status === 'CLOSED') return '已关闭'
    if (status === 'FAILED') return '失败'
    return '待支付'
  }

  const checkOrderStatus = async (orderNo: string, silent = false) => {
    try {
      if (!silent) setChecking(true)
      const latest = await creditService.getRechargeOrder(orderNo)
      setCurrentOrder(latest)

      if (latest.status === 'PAID') {
        clearPolling()
        message.success('充值成功，字数包已到账')
        onSuccess?.()
        onCancel()
        return
      }

      if (latest.status === 'CLOSED' || latest.status === 'FAILED') {
        clearPolling()
        message.warning('订单已失效，请重新下单')
      }
    } catch (error: any) {
      if (!silent) {
        message.error(error?.message || '查询订单状态失败')
      }
    } finally {
      if (!silent) setChecking(false)
    }
  }

  const startPolling = (orderNo: string) => {
    clearPolling()
    pollTimerRef.current = setInterval(() => {
      checkOrderStatus(orderNo, true)
    }, 3000)
  }

  const handleRecharge = async () => {
    if (!config?.enabled) {
      message.warning(config?.reason || '充值功能未开启')
      return
    }

    if (!selectedPackageId) {
      message.warning('请选择套餐')
      return
    }

    const availableTypes = config.supportedPayTypes || []
    if (availableTypes.length === 0) {
      message.warning('当前暂无可用支付方式，请联系管理员')
      return
    }

    if (!availableTypes.includes(payType)) {
      message.warning('请选择可用支付方式')
      return
    }

    try {
      setSubmitting(true)
      const order = await creditService.createRechargeOrder(selectedPackageId, payType)
      setCurrentOrder(order)

      const popup = window.open(order.payUrl, '_blank', 'noopener,noreferrer')
      if (!popup) {
        message.warning('支付页面被浏览器拦截，请允许弹窗后重试')
      } else {
        message.info('已打开支付页面，请完成支付后返回本页')
      }

      startPolling(order.orderNo)
    } catch (error: any) {
      message.error(error?.message || '创建充值订单失败')
    } finally {
      setSubmitting(false)
    }
  }

  const availablePayTypes = config?.supportedPayTypes || []

  return (
    <Modal
      title={null}
      open={visible}
      onCancel={onCancel}
      footer={null}
      width={720}
      centered
      className="pro-recharge-modal"
    >
      <div className="pro-recharge-content">
        <div className="pro-recharge-header">
          <h2>充值中心</h2>
          <p>选择字数包并完成在线支付</p>
        </div>

        {loading ? (
          <div className="loading-container">
            <Spin size="large" />
          </div>
        ) : (
          <>
            {!config?.enabled && (
              <Alert
                type="warning"
                showIcon
                message={config?.reason || '在线充值未开启'}
                style={{ marginBottom: 16 }}
              />
            )}

            <div className="packages-grid">
              {packages.map(pkg => (
                <div
                  key={pkg.id}
                  className={`package-card ${selectedPackageId === pkg.id ? 'selected' : ''}`}
                  onClick={() => setSelectedPackageId(pkg.id)}
                >
                  <div className="package-header">
                    <div className="package-name">{pkg.name}</div>
                    {pkg.sortOrder === 1 && <Tag color="gold">热门</Tag>}
                  </div>
                  <div className="package-credits">
                    <span className="amount">{(pkg.credits / 10000).toFixed(0)}</span>
                    <span className="unit"> 万字</span>
                  </div>
                  <div className="package-price">
                    <span className="currency">¥</span>
                    <span className="value">{pkg.price}</span>
                  </div>
                  <div className="package-desc">{pkg.description || '官方字数包套餐'}</div>
                  {selectedPackageId === pkg.id && (
                    <div className="selected-badge">
                      <CheckCircleOutlined />
                    </div>
                  )}
                </div>
              ))}
            </div>

            <div className="pay-type-row">
              <span className="pay-type-label">支付方式</span>
              <div className="pay-type-actions">
                {availablePayTypes.map(type => (
                  <button
                    key={type}
                    type="button"
                    className={`pay-type-btn ${payType === type ? 'active' : ''}`}
                    onClick={() => setPayType(type)}
                  >
                    {payTypeLabel[type] || type}
                  </button>
                ))}
              </div>
            </div>

            {currentOrder && (
              <div className="order-status-box">
                <div>
                  <div className="order-title">当前订单: {currentOrder.orderNo}</div>
                  <div className="order-meta">
                    状态: <strong>{getStatusText(currentOrder.status)}</strong>
                    {currentOrder.expiredAt ? `，过期时间: ${new Date(currentOrder.expiredAt).toLocaleString('zh-CN')}` : ''}
                  </div>
                </div>
                <Button loading={checking} onClick={() => checkOrderStatus(currentOrder.orderNo)}>
                  我已支付，立即检查
                </Button>
              </div>
            )}

            <div className="recharge-actions">
              <Button
                type="primary"
                size="large"
                className="recharge-btn"
                onClick={handleRecharge}
                disabled={!selectedPackageId || !config?.enabled}
                loading={submitting}
              >
                立即充值
              </Button>
            </div>
          </>
        )}
      </div>
    </Modal>
  )
}

export default RechargeModal
