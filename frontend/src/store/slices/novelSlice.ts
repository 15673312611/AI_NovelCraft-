import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit'
import { novelService } from '@/services/novelService'

export interface Novel {
  id: number
  title: string
  description: string
  genre: string
  status: string
  wordCount: number
  chapterCount: number
  createdAt: string
  updatedAt: string
}

export interface NovelState {
  novels: Novel[]
  currentNovel: Novel | null
  loading: boolean
  error: string | null
  hasMore: boolean // 是否还有更多数据
  currentPage: number // 当前页码
}

const initialState: NovelState = {
  novels: [],
  currentNovel: null,
  loading: false,
  error: null,
  hasMore: true,
  currentPage: 0,
}

// 加载更多小说（支持分页）
export const fetchNovels = createAsyncThunk(
  'novel/fetchNovels', 
  async ({ page = 0, size = 40, append = false }: { page?: number; size?: number; append?: boolean } = {}) => {
    const response = await novelService.getNovels(page, size)
    return { novels: response, append, hasMore: response.length === size }
  }
)

export const createNovel = createAsyncThunk(
  'novel/createNovel',
  async (novelData: { 
    title: string
    description: string
    genre: string
    targetTotalChapters?: number
    wordsPerChapter?: number
    plannedVolumeCount?: number
    totalWordTarget?: number
  }) => {
    const response = await novelService.createNovel(novelData)
    return response
  }
)

export const updateNovel = createAsyncThunk(
  'novel/updateNovel',
  async ({ id, data }: { id: number; data: Partial<Novel> }) => {
    const response = await novelService.updateNovel(id, data)
    return response
  }
)

export const deleteNovel = createAsyncThunk(
  'novel/deleteNovel',
  async (id: number) => {
    await novelService.deleteNovel(id)
    return id
  }
)

const novelSlice = createSlice({
  name: 'novel',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null
    },
    setCurrentNovel: (state, action: PayloadAction<Novel | null>) => {
      state.currentNovel = action.payload
    },
  },
  extraReducers: (builder) => {
    builder
      // Fetch Novels
      .addCase(fetchNovels.pending, (state) => {
        state.loading = true
        state.error = null
      })
      .addCase(fetchNovels.fulfilled, (state, action) => {
        state.loading = false
        const { novels, append, hasMore } = action.payload
        if (append) {
          // 追加模式：添加到现有列表
          state.novels = [...state.novels, ...novels]
          state.currentPage += 1
        } else {
          // 覆盖模式：替换整个列表
          state.novels = novels
          state.currentPage = 0
        }
        state.hasMore = hasMore
      })
      .addCase(fetchNovels.rejected, (state, action) => {
        state.loading = false
        state.error = action.error.message || '获取小说列表失败'
      })
      // Create Novel
      .addCase(createNovel.pending, (state) => {
        state.loading = true
        state.error = null
      })
      .addCase(createNovel.fulfilled, (state, action) => {
        state.loading = false
        state.novels.unshift(action.payload)
      })
      .addCase(createNovel.rejected, (state, action) => {
        state.loading = false
        state.error = action.error.message || '创建小说失败'
      })
      // Update Novel
      .addCase(updateNovel.pending, (state) => {
        state.loading = true
        state.error = null
      })
      .addCase(updateNovel.fulfilled, (state, action) => {
        state.loading = false
        const index = state.novels.findIndex(novel => novel.id === action.payload.id)
        if (index !== -1) {
          state.novels[index] = action.payload
        }
        if (state.currentNovel?.id === action.payload.id) {
          state.currentNovel = action.payload
        }
      })
      .addCase(updateNovel.rejected, (state, action) => {
        state.loading = false
        state.error = action.error.message || '更新小说失败'
      })
      // Delete Novel
      .addCase(deleteNovel.pending, (state) => {
        state.loading = true
        state.error = null
      })
      .addCase(deleteNovel.fulfilled, (state, action) => {
        state.loading = false
        state.novels = state.novels.filter(novel => novel.id !== action.payload)
        if (state.currentNovel?.id === action.payload) {
          state.currentNovel = null
        }
      })
      .addCase(deleteNovel.rejected, (state, action) => {
        state.loading = false
        state.error = action.error.message || '删除小说失败'
      })
  },
})

export const { clearError, setCurrentNovel } = novelSlice.actions
export default novelSlice.reducer 