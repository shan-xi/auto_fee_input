package com.btse.autofeeinput.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientTest {

    @Test
    void parseTuitionFee_picksFirstNumericCellAfterLabel() {
        String html = "<table>"
                + "<tr><td>項目</td><td>金額</td></tr>"
                + "<tr><td>學費</td><td>40,000</td></tr>"
                + "<tr><td>雜費</td><td>5,000</td></tr>"
                + "</table>";
        assertEquals("40000", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_handlesPrefixedLabel() {
        String html = "<table><tr><td>學費(本學期)</td><td>30000</td></tr></table>";
        assertEquals("30000", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_skipsNonNumericCellsThenFallsBack() {
        String html = "<table><tr><td>學費</td><td>備註</td><td>12345</td></tr></table>";
        assertEquals("12345", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_returnsEmptyWhenLabelMissing() {
        String html = "<table><tr><td>雜費</td><td>5000</td></tr></table>";
        assertEquals("", ApiClient.parseTuitionFee(html));
    }

    @Test
    void isCaptchaStillRequired_trueWhenVerifyFieldPresent() {
        String html = "<form><input name=\"txtVerify\" id=\"txtVerify\"/></form>";
        assertTrue(ApiClient.isCaptchaStillRequired(html));
    }

    @Test
    void isCaptchaStillRequired_falseOnResultPage() {
        String html = "<table><tr><td>學費</td><td>40000</td></tr></table>";
        assertFalse(ApiClient.isCaptchaStillRequired(html));
    }

    @Test
    void extractFormState_pullsHiddenFields() {
        String html = "<form>"
                + "<input id=\"__VIEWSTATE\" value=\"vs-value\"/>"
                + "<input id=\"__EVENTVALIDATION\" value=\"ev-value\"/>"
                + "<input id=\"__VIEWSTATEGENERATOR\" value=\"GEN1\"/>"
                + "</form>";
        ApiClient.FormState s = ApiClient.extractFormState(html);
        assertEquals("vs-value", s.viewState);
        assertEquals("ev-value", s.eventValidation);
        assertEquals("GEN1", s.viewStateGenerator);
    }
}