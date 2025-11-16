import api from './api';

export interface AiGenerator {
  id: number;
  name: string;
  description: string;
  icon: string;
  prompt: string;
  category: string;
  sortOrder: number;
  status: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Lấy tất cả generator đang active
 */
export const getAllGenerators = async (): Promise<AiGenerator[]> => {
  try {
    const response: any = await api.get('/ai-generator');
    if (response.success) {
      return response.data;
    }
    throw new Error(response.message || 'Lỗi khi lấy danh sách generator');
  } catch (error: any) {
    console.error('Lỗi getAllGenerators:', error);
    throw error;
  }
};

/**
 * Lấy generator theo category
 */
export const getGeneratorsByCategory = async (category: string): Promise<AiGenerator[]> => {
  try {
    const response: any = await api.get(`/ai-generator/category/${category}`);
    if (response.success) {
      return response.data;
    }
    throw new Error(response.message || 'Lỗi khi lấy generator theo category');
  } catch (error: any) {
    console.error('Lỗi getGeneratorsByCategory:', error);
    throw error;
  }
};

/**
 * Lấy generator theo ID
 */
export const getGeneratorById = async (id: number): Promise<AiGenerator> => {
  try {
    const response: any = await api.get(`/ai-generator/${id}`);
    if (response.success) {
      return response.data;
    }
    throw new Error(response.message || 'Lỗi khi lấy generator');
  } catch (error: any) {
    console.error('Lỗi getGeneratorById:', error);
    throw error;
  }
};

/**
 * Tạo generator mới (admin)
 */
export const createGenerator = async (generator: Partial<AiGenerator>): Promise<AiGenerator> => {
  try {
    const response: any = await api.post('/ai-generator', generator);
    if (response.success) {
      return response.data;
    }
    throw new Error(response.message || 'Lỗi khi tạo generator');
  } catch (error: any) {
    console.error('Lỗi createGenerator:', error);
    throw error;
  }
};

/**
 * Cập nhật generator (admin)
 */
export const updateGenerator = async (id: number, generator: Partial<AiGenerator>): Promise<AiGenerator> => {
  try {
    const response: any = await api.put(`/ai-generator/${id}`, generator);
    if (response.success) {
      return response.data;
    }
    throw new Error(response.message || 'Lỗi khi cập nhật generator');
  } catch (error: any) {
    console.error('Lỗi updateGenerator:', error);
    throw error;
  }
};

/**
 * Xóa generator (admin)
 */
export const deleteGenerator = async (id: number): Promise<void> => {
  try {
    const response: any = await api.delete(`/ai-generator/${id}`);
    if (!response.success) {
      throw new Error(response.message || 'Lỗi khi xóa generator');
    }
  } catch (error: any) {
    console.error('Lỗi deleteGenerator:', error);
    throw error;
  }
};

