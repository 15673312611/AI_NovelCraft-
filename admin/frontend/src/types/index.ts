export interface User {
  id: number
  username: string
  email: string
  role: string
  status: string
  createdAt: string
}

export interface Novel {
  id: number
  title: string
  author: string
  genre: string
  status: string
  chapterCount: number
  wordCount: number
  createdAt: string
}

export interface AITask {
  id: number
  name: string
  type: string
  status: string
  progress: number
  cost: number
  createdAt: string
  completedAt?: string
}

export interface Template {
  id: number
  name: string
  category: string
  content: string
  usageCount: number
  createdAt: string
}

export interface DashboardStats {
  totalUsers: number
  totalNovels: number
  totalAITasks: number
  totalCost: number
}
