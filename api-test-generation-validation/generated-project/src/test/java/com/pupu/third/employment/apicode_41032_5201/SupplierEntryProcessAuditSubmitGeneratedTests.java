package com.pupu.third.employment.apicode_41032_5201;

import com.pupu.put.common.BaseTest;
import com.pupu.put.configuration.dataprovider.DataDriveUtil;
import com.pupu.put.configuration.dto.FrameConfigInfo;
import com.pupu.third.employment.api.ThirdEmploymentApi;
import com.pupu.third.employment.dto.JsonResponse;
import com.pupu.third.employment.ro.SupplierAuditSubmitRO;
import java.lang.reflect.Method;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author AI
 * @create 2026-07-12
 * @desc 合作用工-提交审核首轮接口契约脚手架验证
 */
public class SupplierEntryProcessAuditSubmitGeneratedTests extends BaseTest {

    @Autowired
    private ThirdEmploymentApi thirdEmploymentApi;

    @DataProvider(name = "auditSubmitContractDataProvider")
    public Object[][] prepareTestData(Method method) {
        return DataDriveUtil.loadTestData(method, "合作用工-提交审核-契约验证", true);
    }

    @Test(dataProvider = "auditSubmitContractDataProvider", description = "合作用工-提交审核首轮接口契约脚手架验证")
    public void auditSubmitContractTests(
            FrameConfigInfo config,
            SupplierAuditSubmitRO request,
            JsonResponse<Boolean> expect
    ) {
        // 调用被测接口
        JsonResponse<Boolean> actual = thirdEmploymentApi.auditSubmit(request);

        // 比对结果
        List<String> diffFields = DataDriveUtil.diffFieldValue(expect, actual);
        Assert.assertTrue(CollectionUtils.isEmpty(diffFields),
                "字段对比失败:\n" + String.join("\n", diffFields));
    }
}
