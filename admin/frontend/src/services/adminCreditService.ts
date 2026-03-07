import request from './request'

export interface UserCredit {
  id: number
  userId: number
  balance: number
  totalRecharged: number
  totalConsumed: number
  totalGifted: number
  frozenAmount: number
  username?: string
  email?: string
  createdAt: string
  updatedAt: string
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
  operatorId?: number
  username?: string
  operatorName?: string
  createdAt: string
}

export interface CreditStatistics {
  totalBalance: number
  totalConsumed: number
  totalRecharged: number
  todayConsumed: number
  todayRecharged: number
  monthConsumed: number
  monthRecharged: number
}

export interface ModelUsageStats {
  modelId: string
  callCount: number
  totalInputTokens: number
  totalOutputTokens: number
  totalCost: number
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

export interface YiPayConfig {
  enabled: boolean
  gatewayUrl: string
  pid: string
  key: string
  notifyUrl: string
  returnUrl: string
  orderExpireMinutes: number
}

export const adminCreditService = {
  getUserCredits: (params?: { page?: number; size?: number; keyword?: string }) => {
    return request.get<{ content: UserCredit[]; totalElements: number; totalPages: number }>('/credits/users', { params })
  },

  getUserCredit: (userId: number) => {
    return request.get<UserCredit>(`/credits/users/${userId}`)
  },

  recharge: (userId: number, data: { amount: number; description?: string; operatorId?: number }) => {
    return request.post(`/credits/users/${userId}/recharge`, data)
  },

  gift: (userId: number, data: { amount: number; description?: string; operatorId?: number }) => {
    return request.post(`/credits/users/${userId}/gift`, data)
  },

  adjustBalance: (userId: number, data: { amount: number; description?: string; operatorId?: number }) => {
    return request.post(`/credits/users/${userId}/adjust`, data)
  },

  getTransactions: (params?: { page?: number; size?: number; userId?: number; type?: string }) => {
    return request.get<{ content: CreditTransaction[]; totalElements: number; totalPages: number }>('/credits/transactions', { params })
  },

  getStatistics: () => {
    return request.get<CreditStatistics>('/credits/statistics')
  },

  getModelUsageStats: (days: number = 30) => {
    return request.get<ModelUsageStats[]>('/credits/model-usage', { params: { days } })
  },

  getPackages: () => {
    return request.get<CreditPackage[]>('credits/packages')
  },

  createPackage: (data: Partial<CreditPackage>) => {
    return request.post<CreditPackage>('credits/packages', data)
  },

  updatePackage: (id: number, data: Partial<CreditPackage>) => {
    return request.put<CreditPackage>(`credits/packages/${id}`, data)
  },

  deletePackage: (id: number) => {
    return request.delete(`credits/packages/${id}`)
  },

  getRegistrationBonus: () => {
    return request.get<string>('credits/config/registration-bonus')
  },

  updateRegistrationBonus: (amount: string) => {
    return request.put('credits/config/registration-bonus', { amount })
  },

  getPaymentConfig: () => {
    return request.get<YiPayConfig>('credits/config/payment')
  },

  updatePaymentConfig: (config: Partial<YiPayConfig>) => {
    return request.put('credits/config/payment', config)
  }
}
