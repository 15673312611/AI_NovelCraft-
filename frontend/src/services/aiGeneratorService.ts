import api from './api';

export interface AiGenerator {
  id: number;
  name: string;
  description: string;
  icon: string;
  category: string;
  sortOrder: number;
  status: number;
  createdAt: string;
  updatedAt: string;
}

export const getAllGenerators = async (): Promise<AiGenerator[]> => {
  try {
    const response: any = await api.get('/ai-generator');
    if (response.success) {
      return response.data;
    }
    throw new Error(response.message || 'Failed to load generators');
  } catch (error: any) {
    console.error('getAllGenerators failed:', error);
    throw error;
  }
};

export const getGeneratorById = async (id: number): Promise<AiGenerator> => {
  try {
    const response: any = await api.get(`/ai-generator/${id}`);
    if (response.success) {
      return response.data;
    }
    throw new Error(response.message || 'Failed to load generator');
  } catch (error: any) {
    console.error('getGeneratorById failed:', error);
    throw error;
  }
};
