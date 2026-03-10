import api from './api'

export type RechargePayType = 'alipay' | 'wxpay' | 'qqpay' | 'cashier'

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
  type: 'RECHARGE' | 'CONSUME' | 'GIFT' | 'REFUND' | 'ADMIN_ADJUST'
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

export interface AIModel {
  id: number
  modelId: string
  displayName: string
  provider: string
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

export const creditService = {
  getBalance: async (): Promise<UserCreditInfo> => {
    const response: any = await api.get('/credits/balance')
    return response.data
  },

  getTransactions: async (page: number = 0, size: number = 20) => {
    const response: any = await api.get('/credits/transactions', { params: { page, size } })
    return response.data
  },

  getAvailableModels: async (): Promise<AIModel[]> => {
    const response: any = await api.get('/credits/models')
    return response.data
  },

  getPackages: async (): Promise<CreditPackage[]> => {
    const response: any = await api.get('/credits/packages')
    return response.data
  },

  getRechargeConfig: async (): Promise<RechargeConfig> => {
    const response: any = await api.get('/credits/recharge/config')
    return response.data
  },

  createRechargeOrder: async (packageId: number, payType?: RechargePayType): Promise<RechargeOrder> => {
    const response: any = await api.post('/credits/recharge/orders', { packageId, payType })
    return response.data
  },

  getRechargeOrder: async (orderNo: string): Promise<RechargeOrder> => {
    const response: any = await api.get(`/credits/recharge/orders/${orderNo}`)
    return response.data
  },
}

export default creditService
