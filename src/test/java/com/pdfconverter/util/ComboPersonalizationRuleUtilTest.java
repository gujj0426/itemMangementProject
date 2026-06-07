package com.pdfconverter.util;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboPersonalizationRuleUtilTest {

    @Test
    void order4060824257_cufflinkPicture_tieClipLmFont4() {
        String pers = "#4 font, LM for tie clip, picture for cuff\nlinks will follow";

        ItemDetail cuff = new ItemDetail();
        cuff.setOrderType(OrderType.CUFFLINK);
        cuff.setPersonalization(pers);
        cuff.setPersonalizationTextForLlm(PersonalizationSlotSplitter.excerptForCufflink(pers));
        cuff.setEngravingFanOutApplied(true);

        ItemDetail tie = new ItemDetail();
        tie.setOrderType(OrderType.TIE_CLIP);
        tie.setPersonalization(pers);
        tie.setPersonalizationTextForLlm(PersonalizationSlotSplitter.excerptForTieClip(pers));
        tie.setEngravingFanOutApplied(true);

        assertTrue(ComboPersonalizationRuleUtil.tryApplyRuleBasedIntent(cuff));
        assertEquals("人物头像(见附图)", cuff.getLlmDesignStyle());
        assertEquals("", cuff.getLlmEngravingContent());

        assertTrue(ComboPersonalizationRuleUtil.tryApplyRuleBasedIntent(tie));
        assertEquals("LM", tie.getLlmEngravingContent());
        assertEquals("Font 4(no)", tie.getLlmFont());
    }
}
