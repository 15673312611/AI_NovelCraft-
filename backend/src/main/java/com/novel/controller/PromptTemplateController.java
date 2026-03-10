package com.novel.controller;

import com.novel.common.Result;
import com.novel.common.security.AuthUtils;
import com.novel.domain.entity.PromptTemplate;
import com.novel.service.PromptTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 闂備礁婀辩划顖炲礉濡ゅ懎桅婵椴哥€氭氨鈧箍鍎遍弸鐑樼瑹閳ь剙顕ｉ崜浣诡偨缂佽泛顩眛roller
 */
@RestController
@RequestMapping("/prompt-templates")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class PromptTemplateController {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateController.class);

    @Autowired
    private PromptTemplateService promptTemplateService;


    /**
     * 闂備礁鎼粔鐑斤綖婢跺﹦鏆ゅ☉鎿冩惓闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉仜閻旂厧鐒洪柛鎰ㄦ櫇閸戯繝鏌ｉ悩鍙夌カ闂傚嫬瀚板畷?
     */
    @GetMapping("/{id}")
    public Result<PromptTemplate> getTemplateById(@PathVariable Long id) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            PromptTemplate template = promptTemplateService.getById(id);
            if (template == null) {
                return Result.error("模板不存在");
            }
            
            // 濠德板€楁慨鎾儗娓氣偓閹焦寰勯幇顒傞獓闂佸憡鍔﹂崰妤咁敁濞嗘挻鐓ユ繛鎴炵懆婢规鎲搁幎濠傛处閸ゅ嫰鏌ら幁鎺戝姢闁告瑢鍋撻梻浣规た娴滄粓顢栭崨鏉戠煑闁哄洨鍎愰崵鏇㈡煃瑜滈崜娆掔亽闂佹枼鏅涢崯顖滄閺屻儲鐓忛柛鈩冩礈椤︼箓鏌ｉ敐鍛仮鐎殿噮鍓涢幉鎾礋閳ь剚绗熼埀顒€顕ｉ崼鏇炵閹艰揪绱曟禒鎰版⒑閻撳寒娈犲ù婊勭矌閹广垺绗熼埀顒勫箚閸愵喖绀嬫い鎴犲枍缁舵艾顕?
            String type = template.getType();
            if (!"official".equals(type) && 
                !"public".equals(type) && 
                (template.getUserId() == null || !template.getUserId().equals(userId))) {
                return Result.error("无权查看该模板");
            }

            // 濠电偛顕慨鎾箠閹捐鍚规繝濠傛噳閸嬫挾鎲撮崟顓犲彎缂備胶濮烽崰搴ょ亽闂佹枼鏅涢崯顖滄閺屻儲鐓曢煫鍥у缁佺増銇勯弬璺ㄧ婵炵厧顭峰顒勫箰鎼达綆妲梻浣告啞閸旀洟骞婃惔顭戞晩闁规壆澧楅弲顒勬倶閻愬灚娅曢柡鍡╁弮閺?闂備胶顭堝ù姘跺礈濞嗘垶顫曢柟瀛樻儕閻旂厧鐒洪柛鎰ㄦ櫇閸戔剝绻涢幋鐐村碍缂佸顥撻幑銏ゅ箣閿曗偓閻?
            if (!"custom".equals(type)) {
                template.setContent(null);
            }
            
            return Result.success(template);
        } catch (Exception e) {
            logger.error("获取模板详情失败", e);
            return Result.error("获取模板详情失败: " + e.getMessage());
        }
    }

    /**
     * 闂備礁鎲＄敮妤冪矙閹寸姷纾介柟鍓х帛閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻銈嗙附婢跺鐩庨梺浼欑悼閸嬫挾绮欐繝鍐ㄧ窞閻庯絽妫旂欢姘嚕?
     */
    @PostMapping
    public Result<PromptTemplate> createTemplate(@RequestBody Map<String, Object> request) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            
            String name = (String) request.get("name");
            String content = (String) request.get("content");
            String description = (String) request.get("description");
            
            if (name == null || name.trim().isEmpty()) {
                return Result.error("模板名称不能为空");
            }
            if (content == null || content.trim().isEmpty()) {
                return Result.error("模板内容不能为空");
            }
            
            PromptTemplate template = promptTemplateService.createCustomTemplate(
                userId, name, content, "chapter", description
            );
            // 濠电偞鍨堕幐鍝ョ矓閹绢噮鏁嬫い鏇楀亾鐎规洘绮岄濂稿炊閺堢數纾介梺鍝勵槴閺呮粓寮婚妸銉冩椽寮介鐐殿唽闂佸綊鍋婇崰鎾寸濞戙垺鍋ｉ柛銉戝憛銏ゆ煕閻愬樊鍤熼柍?
            template.setContent(null);
            
            return Result.success(template);
        } catch (Exception e) {
            logger.error("创建模板失败", e);
            return Result.error("创建模板失败: " + e.getMessage());
        }
    }

    /**
     * 闂備礁鎼ú銈夋偤閵娾晛钃熷┑鐘叉处閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻銈嗙附婢跺鐩庨梺浼欑悼閸嬫挾绮欐繝鍐ㄧ窞閻庯絽妫旂欢姘嚕?
     */
    @PutMapping("/{id}")
    public Result<String> updateTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            
            String name = (String) request.get("name");
            String content = (String) request.get("content");
            String category = (String) request.get("category");
            String description = (String) request.get("description");
            
            boolean success = promptTemplateService.updateCustomTemplate(
                id, userId, name, content, "chapter", description
            );
            
            if (success) {
                return Result.success("更新成功");
            } else {
                return Result.error("更新失败");
            }
        } catch (Exception e) {
            logger.error("更新模板失败", e);
            return Result.error("更新模板失败: " + e.getMessage());
        }
    }

    /**
     * 闂備礁鎲＄敮鐐寸箾閳ь剚绻涢崨顓㈠弰闁诡喕绮欐俊鎼佹晝閳ь剟鎮￠弴銏＄厾濠靛倸顦花濠氭煟閿濆洤浜剧紒瀣攻瀵板嫮鈧絽妫旂欢姘嚕?
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteTemplate(@PathVariable Long id) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            
            boolean success = promptTemplateService.deleteCustomTemplate(id, userId);
            
            if (success) {
                return Result.success("删除成功");
            } else {
                return Result.error("删除失败");
            }
        } catch (Exception e) {
            logger.error("删除模板失败", e);
            return Result.error("删除模板失败: " + e.getMessage());
        }
    }


    /**
     * 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墮缁€鍌涖亜閹哄棗浜剧紓浣介哺閸ㄤ絻鐏掗梺鏂ユ櫅閸燁垳娆㈤弻銉︾厱闁圭儤鎼╁▓娆撴煏?
     */
    @GetMapping("/public")
    public Result<List<PromptTemplate>> getPublicTemplates(@RequestParam(required = false) String category) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            logger.info("获取公开模板列表: userId={}, category={}", userId, category);
            List<PromptTemplate> templates = promptTemplateService.getPublicTemplates(userId, category);
            logger.info("获取公开模板成功: 数量={}", templates.size());
            return Result.success(templates);
        } catch (Exception e) {
            logger.error("获取公开模板列表失败", e);
            return Result.error("获取公开模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻銈嗙附婢跺鐩庨梺浼欑悼閸嬫挾绮欐繝鍐ㄧ窞閻庯絽妫旂欢姘嚕閸洖鍨傛い鏃囧亹娴犳岸鏌?
     */
    @GetMapping("/custom")
    public Result<List<PromptTemplate>> getUserCustomTemplates(@RequestParam(required = false) String category) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            List<PromptTemplate> templates = promptTemplateService.getUserCustomTemplates(userId, category);
            return Result.success(templates);
        } catch (Exception e) {
            logger.error("获取自定义模板列表失败", e);
            return Result.error("获取自定义模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 闂備礁鍚嬮崕鎶藉床閼艰翰浜归柛銉墯閸嬨劑鏌曟繝蹇曠暠闁绘挻娲熼弻锟犲焵椤掑嫬绠氶柣妤€鐗滃Λ蹇涙⒑濮瑰洤濡奸悗姘煎弨閸燁垶姊洪崫鍕垫Ц闁诲繑绻堝畷褰掝敂閸℃ê浠?
     */
    @GetMapping("/favorites")
    public Result<List<PromptTemplate>> getUserFavoriteTemplates(@RequestParam(required = false) String category) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            List<PromptTemplate> templates = promptTemplateService.getUserFavoriteTemplates(userId, category);
            return Result.success(templates);
        } catch (Exception e) {
            logger.error("获取收藏模板列表失败", e);
            return Result.error("获取收藏模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 闂備浇銆€閸嬫捇鏌熼婊冾暭妞ゃ儲鍨舵穱濠囶敍濡も偓婢у弶绻?
     */
    @PostMapping("/{id}/favorite")
    public Result<String> favoriteTemplate(@PathVariable Long id) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            boolean success = promptTemplateService.favoriteTemplate(userId, id);
            if (success) {
                return Result.success("收藏成功");
            } else {
                return Result.error("收藏失败");
            }
        } catch (Exception e) {
            logger.error("收藏模板失败", e);
            return Result.error("收藏模板失败: " + e.getMessage());
        }
    }

    /**
     * 闂備礁鎲￠悷锕傛偋濡ゅ啰鐭撻柣鎴ｆ缂佲晠鏌熼婊冾暭妞ゃ儲鍨舵穱濠囶敍濡も偓婢у弶绻?
     */
    @DeleteMapping("/{id}/favorite")
    public Result<String> unfavoriteTemplate(@PathVariable Long id) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            boolean success = promptTemplateService.unfavoriteTemplate(userId, id);
            if (success) {
                return Result.success("取消收藏成功");
            } else {
                return Result.error("取消收藏失败");
            }
        } catch (Exception e) {
            logger.error("取消收藏模板失败", e);
            return Result.error("取消收藏模板失败: " + e.getMessage());
        }
    }

}
