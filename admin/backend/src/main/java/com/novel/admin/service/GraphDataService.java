package com.novel.admin.service;

import com.novel.admin.dto.GraphDataDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GraphDataService {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphDataService.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * 从客户端后端获取图谱数据
     */
    public GraphDataDTO getGraphData(Long novelId) {
        try {
            // 调用客户端后端的图谱API
            String url = "http://localhost:8080/agentic/graph/data/" + novelId;
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && "success".equals(response.get("status"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                
                if (data != null) {
                    GraphDataDTO graphData = new GraphDataDTO();
                    graphData.setCharacterStates((java.util.List<GraphDataDTO.CharacterStateDTO>) data.get("characterStates"));
                    graphData.setRelationshipStates((java.util.List<GraphDataDTO.RelationshipStateDTO>) data.get("relationshipStates"));
                    graphData.setOpenQuests((java.util.List<GraphDataDTO.OpenQuestDTO>) data.get("openQuests"));
                    graphData.setEvents((java.util.List<GraphDataDTO.EventDTO>) data.get("events"));
                    graphData.setTotalCharacterStates((Integer) data.get("totalCharacterStates"));
                    graphData.setTotalRelationshipStates((Integer) data.get("totalRelationshipStates"));
                    graphData.setTotalOpenQuests((Integer) data.get("totalOpenQuests"));
                    graphData.setTotalEvents((Integer) data.get("totalEvents"));
                    
                    return graphData;
                }
            }
            
            logger.warn("获取图谱数据失败: novelId={}, response={}", novelId, response);
            return null;
            
        } catch (Exception e) {
            logger.error("获取图谱数据异常: novelId={}", novelId, e);
            return null;
        }
    }
}
