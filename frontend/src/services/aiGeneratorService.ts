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
 * Lấy tất c�?generator đang active
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




