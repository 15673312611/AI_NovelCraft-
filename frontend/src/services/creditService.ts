import api from './api'

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

export interface CostEstimate {
  estimatedCost: number
  modelId: string
  modelName: string
  inputPricePer1k: number
  outputPricePer1k: number
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
  paymentType: 'alipay' | 'wxpay'
  status: 'PENDING' | 'PAID' | 'CLOSED' | 'FAILED'
  payUrl: string
  paidAt?: string | null
  expiredAt?: string | null
  createdAt?: string | null
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

  estimateCost: async (modelId?: string, inputText?: string, estimatedOutputTokens?: number): Promise<CostEstimate> => {
    const response: any = await api.post('/credits/estimate', {
      modelId,
      inputText,
      estimatedOutputTokens,
    })
    return response.data
  },

  checkBalance: async (amount: number): Promise<{ sufficient: boolean; availableBalance: number; shortfall: number }> => {
    const response: any = await api.post('/credits/check', { amount })
    return response.data
  },

  getAvailableModels: async (): Promise<AIModel[]> => {
    const response: any = await api.get('/credits/models')
    return response.data
  },

  getDefaultModel: async (): Promise<AIModel> => {
    const response: any = await api.get('/credits/models/default')
    return response.data
  },

  getPackages: async (): Promise<CreditPackage[]> => {
    const response: any = await api.get('/credits/packages')
    return response.data
  },

  createRechargeOrder: async (packageId: number, payType: 'alipay' | 'wxpay'): Promise<RechargeOrder> => {
    const response: any = await api.post('/credits/recharge/orders', { packageId, payType })
    return response.data
  },

  getRechargeOrder: async (orderNo: string): Promise<RechargeOrder> => {
    const response: any = await api.get(`/credits/recharge/orders/${orderNo}`)
    return response.data
  },
}

export default creditService
