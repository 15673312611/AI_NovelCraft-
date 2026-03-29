import api from './api'

export type RechargePayType = 'alipay' | 'wxpay' | 'qqpay' | 'cashier'

interface ResultEnvelope<T> {
  code?: number | string
  message?: string
  data?: T
  success?: boolean
}

export interface UserCreditInfo {
  balance: number
  availableBalance: number
  frozenAmount: number
  totalRecharged: number
  totalConsumed: number
  totalGifted: number
  todayConsumption: number
  monthConsumption: number
  lowBalance: boolean
  warningThreshold: number
  dailyFreeEnabled: boolean
  dailyFreeBalance: number
  dailyFreeAmount: number
  dailyFreeLastReset: string | null
  totalAvailableBalance: number
}

export interface CreditTransaction {
  id: number
  userId: number
  type: 'RECHARGE' | 'CONSUME' | 'GIFT' | 'REDEEM_CODE' | 'REFUND' | 'ADMIN_ADJUST'
  amount: number
  balanceBefore: number
  balanceAfter: number
  aiTaskId?: number
  modelId?: string
  inputTokens?: number
  outputTokens?: number
  description?: string
  createdAt: string
}

export interface RedeemCodeResult {
  code: string
  name?: string
  credits: number
  balance: number
  totalAvailableBalance: number
  usedAt: string
  usedCount?: number
  maxTotalUses?: number | null
  maxUsesPerUser?: number
  remainingTotalUses?: number | null
}

export interface AIModel {
  id: number
  displayName: string
  maxTokens: number
  inputPricePer1k: number
  outputPricePer1k: number
  costMultiplier: number
  temperature?: number
  available: boolean
  isDefault: boolean
  description?: string
}
export interface CreditPackage {
  id: number
  name: string
  price: number
  credits: number
  description?: string
  isActive: boolean
  sortOrder: number
}

export interface RechargeOrder {
  orderNo: string
  packageId: number
  packageName: string
  packagePrice: number
  packageCredits: number
  paymentType: RechargePayType
  status: 'PENDING' | 'PAID' | 'CLOSED' | 'FAILED'
  payUrl: string
  payAction?: string
  payMethod?: 'GET' | 'POST'
  payParams?: Record<string, string>
  paidAt?: string | null
  expiredAt?: string | null
  createdAt?: string | null
}

export interface RechargeConfig {
  enabled: boolean
  provider: string
  supportedPayTypes: RechargePayType[]
  defaultPayType: RechargePayType
  orderExpireMinutes: number
  reason?: string
}

const unwrapResult = <T>(response: ResultEnvelope<T> | T): T => {
  if (response && typeof response === 'object') {
    const payload = response as ResultEnvelope<T>

    if (typeof payload.success === 'boolean') {
      if (!payload.success) {
        throw new Error(payload.message || '请求失败')
      }
      return payload.data as T
    }

    if (payload.code !== undefined) {
      const code = Number(payload.code)
      if (!Number.isNaN(code) && code !== 200) {
        throw new Error(payload.message || '请求失败')
      }
      return payload.data as T
    }
  }

  return response as T
}

export const creditService = {
  getBalance: async (): Promise<UserCreditInfo> => {
    return unwrapResult<UserCreditInfo>(await api.get('/credits/balance'))
  },

  getTransactions: async (page: number = 0, size: number = 20) => {
    return unwrapResult(await api.get('/credits/transactions', { params: { page, size } }))
  },

  getAvailableModels: async (): Promise<AIModel[]> => {
    return unwrapResult<AIModel[]>(await api.get('/credits/models'))
  },

  getPackages: async (): Promise<CreditPackage[]> => {
    return unwrapResult<CreditPackage[]>(await api.get('/credits/packages'))
  },

  getRechargeConfig: async (): Promise<RechargeConfig> => {
    return unwrapResult<RechargeConfig>(await api.get('/credits/recharge/config'))
  },

  createRechargeOrder: async (
    packageId: number,
    payType?: RechargePayType,
    redirectPath?: string
  ): Promise<RechargeOrder> => {
    return unwrapResult<RechargeOrder>(await api.post('/credits/recharge/orders', { packageId, payType, redirectPath }))
  },

  getRechargeOrder: async (orderNo: string): Promise<RechargeOrder> => {
    return unwrapResult<RechargeOrder>(await api.get(`/credits/recharge/orders/${orderNo}`))
  },

  redeemCode: async (code: string): Promise<RedeemCodeResult> => {
    return unwrapResult<RedeemCodeResult>(await api.post('/credits/redeem', { code }))
  },
}

export default creditService
