package com.btse.autofeeinput.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiClientTest {

    @Test
    void parseTuitionFee_bothClasses_formatsWithSlash() {
        String html =
                "<table>"
                        + "<tr><th>項目</th><th>半日班</th><th>全日班</th></tr>"
                        + "<tr><td>上學期計  6  個月</td><td>94,626</td><td>94,626</td></tr>"
                        + "</table>";
        assertEquals("學費 半日班94,626/全日班94,626", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_onlyFullDay() {
        String html =
                "<table>"
                        + "<tr><th>項目</th><th>半日班</th><th>全日班</th></tr>"
                        + "<tr><td>全學期總收費</td><td></td><td>120,000</td></tr>"
                        + "</table>";
        assertEquals("學費 全日班120,000", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_onlyHalfDay() {
        String html =
                "<table>"
                        + "<tr><th>項目</th><th>半日班</th><th>全日班</th></tr>"
                        + "<tr><td>總計</td><td>50,000</td><td></td></tr>"
                        + "</table>";
        assertEquals("學費 半日班50,000", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_picksFirstMatchingRow() {
        String html =
                "<table>"
                        + "<tr><th>項目</th><th>半日班</th><th>全日班</th></tr>"
                        + "<tr><td>雜費</td><td>5,000</td><td>5,000</td></tr>"
                        + "<tr><td>上學期計 6 個月</td><td>40,000</td><td>50,000</td></tr>"
                        + "<tr><td>總計</td><td>80,000</td><td>100,000</td></tr>"
                        + "</table>";
        assertEquals("學費 半日班40,000/全日班50,000", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_handlesColspanHeaderWithFeeBreakdown() {
        String html =
                "<table>"
                        + "<tr><th rowspan=\"2\">項目</th>"
                        + "    <th colspan=\"2\">半日班</th>"
                        + "    <th colspan=\"2\">全日班</th></tr>"
                        + "<tr><th>學費</th><th>雜費</th>"
                        + "    <th>學費</th><th>雜費</th></tr>"
                        + "<tr><td>全學期總收費</td>"
                        + "    <td>94,626</td><td>5,000</td>"
                        + "    <td>120,000</td><td>5,000</td></tr>"
                        + "</table>";
        assertEquals("學費 半日班94,626/全日班120,000", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_findsRowInsideNestedLayoutTable() {
        String html =
                "<table>"
                        + "<tr><td>page header</td></tr>"
                        + "<tr><td>"
                        + "  <table>"
                        + "    <tr><th>項目</th><th>半日班</th><th>全日班</th></tr>"
                        + "    <tr><td>全學期總收費</td><td>94,626</td><td>120,000</td></tr>"
                        + "  </table>"
                        + "</td></tr>"
                        + "</table>";
        assertEquals("學費 半日班94,626/全日班120,000", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_labelWithExtraSuffix() {
        String html =
                "<table>"
                        + "<tr><th>項目</th><th>半日班</th><th>全日班</th></tr>"
                        + "<tr><td>全學期總收費 (元)</td><td>94,626</td><td>120,000</td></tr>"
                        + "</table>";
        assertEquals("學費 半日班94,626/全日班120,000", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_moeRealLayout_fullDayOnly() {
        // Mirrors the live ap.ece.moe.edu.tw ChgListData_GridView1 layout:
        // one header table (上學期計 6 個月 / 下學期計 6 個月) followed by the
        // 10-column data grid with colspan=2 totals on the 全學期總收費 row.
        String html =
                ""
                        + "<div>"
                        + "  <table>"
                        + "    <tr>"
                        + "      <td>&nbsp;</td>"
                        + "      <td><b>上學期計&nbsp;<span>6</span>&nbsp; 個月</b></td>"
                        + "      <td><b>下學期計&nbsp;<span>6</span>&nbsp; 個月</b></td>"
                        + "    </tr>"
                        + "  </table>"
                        + "  <table>"
                        + "    <tr>"
                        + "      <th colspan=\"2\">收費項目</th>"
                        + "      <th>收費期間</th>"
                        + "      <th>半日班</th><th>小計</th>"
                        + "      <th>全日班</th><th>小計</th>"
                        + "      <th>半日班</th><th>小計</th>"
                        + "      <th>全日班</th><th>小計</th>"
                        + "    </tr>"
                        + "    <tr>"
                        + "      <td colspan=\"2\">學費</td>"
                        + "      <td>學期</td>"
                        + "      <td></td><td></td>"
                        + "      <td><span>15,000</span></td><td><span>15,000</span></td>"
                        + "      <td></td><td></td>"
                        + "      <td><span>15,000</span></td><td><span>15,000</span></td>"
                        + "    </tr>"
                        + "    <tr>"
                        + "      <td colspan=\"2\">雜費</td>"
                        + "      <td>月</td>"
                        + "      <td></td><td></td>"
                        + "      <td><span>4,471</span></td><td><span>26,826</span></td>"
                        + "      <td></td><td></td>"
                        + "      <td><span>4,471</span></td><td><span>26,826</span></td>"
                        + "    </tr>"
                        + "    <tr>"
                        + "      <td colspan=\"2\">全學期總收費</td>"
                        + "      <td>總計</td>"
                        + "      <td colspan=\"2\"></td>"
                        + "      <td colspan=\"2\"><span>94,626</span></td>"
                        + "      <td colspan=\"2\"></td>"
                        + "      <td colspan=\"2\"><span>94,626</span></td>"
                        + "    </tr>"
                        + "  </table>"
                        + "</div>";
        assertEquals("學費 全日班94,626", ApiClient.parseTuitionFee(html));
    }

    @Test
    void parseTuitionFee_returnsEmptyWhenNoMatchingRow() {
        String html =
                "<table>"
                        + "<tr><th>項目</th><th>半日班</th><th>全日班</th></tr>"
                        + "<tr><td>雜費</td><td>5,000</td><td>5,000</td></tr>"
                        + "</table>";
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
        String html =
                "<form>"
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
