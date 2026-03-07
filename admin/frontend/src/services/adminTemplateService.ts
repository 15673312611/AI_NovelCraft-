import request from './request'

export const adminTemplateService = {
  getTemplates: (params?: { category?: string; page?: number; size?: number }) => {
    return request.get('/templates', { params })
  },

  getTemplateById: (id: number) => {
    return request.get(`/templates/${id}`)
  },

  createTemplate: (data: any) => {
    return request.post('/templates', data)
  },

  updateTemplate: (id: number, data: any) => {
    return request.put(`/templates/${id}`, data)
  },

  deleteTemplate: (id: number) => {
    return request.delete(`/templates/${id}`)
  },

  setDefaultTemplate: (id: number) => {
    return request.post(`/templates/${id}/set-default`)
  },

  updateTemplateSortOrder: (id: number, sortOrder: number) => {
    return request.put(`/templates/${id}/sort-order`, { sortOrder })
  },

  batchUpdateSortOrder: (templateIds: number[]) => {
    return request.post('/templates/sort-order', { templateIds })
  },
}
