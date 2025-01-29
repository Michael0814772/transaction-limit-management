package com.michael.limit.management.service.impl;

import com.michael.limit.management.dto.DailyLimitUsageDto.CifDto;
import com.michael.limit.management.dto.DailyLimitUsageDto.DailyLimitUsageRequestDto;
import com.michael.limit.management.dto.DailyLimitUsageDto.TransferDestinationDetailsDto;
import com.michael.limit.management.exception.exceptionMethod.MyCustomizedException;
import com.michael.limit.management.repository.CustomerLimitRepository;
import com.michael.limit.management.repository.ProductTypeRepository;
import com.michael.limit.management.repository.ServiceTypeRepository;
import com.michael.limit.management.utils.HelperUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class DailyLimitUsageServiceImplTest {

    @InjectMocks
    private DailyLimitUsageServiceImpl dailyLimitUsageService;

    @Mock
    private HelperUtils helperUtils;

    @Mock
    private CustomerLimitRepository customerLimitRepository;

    @Mock
    private ServiceTypeRepository serviceTypeRepository;

    @Mock
    private ProductTypeRepository productTypeRepository;

    // Add more mock dependencies as needed

    @Test
    public void testDailyLimit() {
        // Create a mock DailyLimitUsageRequestDto object
        DailyLimitUsageRequestDto requestDto = getRequestDto();

        // Create a mock String object for the URL
        String url = "https://example.com";

        // Create a mock String object for the serviceToken
        String serviceToken = "your_service_token";

        // Create a mock String object for the serviceIpAddress
        String serviceIpAddress = "your_service_ip_address";

        // Call the dailyLimit method and assert that it does not throw any exceptions
//        assertDoesNotThrow(() -> dailyLimitUsageService.dailyLimit(requestDto, url, serviceToken, serviceIpAddress));
    }

    private DailyLimitUsageRequestDto getRequestDto() {
        DailyLimitUsageRequestDto requestDto = new DailyLimitUsageRequestDto();
        // Set the necessary values for the requestDto
        requestDto.setAccountNumber("XX55815555");
        requestDto.setDestinationRequestId(0);
        requestDto.setServiceTypeId(1);
        requestDto.setTransferType("instant");
        requestDto.setChannelId(3);
        requestDto.setRequestDestinationId("STI-10199990110811-3");

        List<TransferDestinationDetailsDto> amountDetails = requestDto.getTransferDestinationDetails();
        TransferDestinationDetailsDto transferDestinationDetail = new TransferDestinationDetailsDto();
        transferDestinationDetail.setDestination("STS");
        transferDestinationDetail.setAmount(BigDecimal.valueOf(4000));
        amountDetails.add(transferDestinationDetail);

        return requestDto;
    }

    @Test
    public void getCifIdAndType() {
        log.info("getting account details...");

        String accountNumber = "555555";

        CifDto cifDto = new CifDto();

        CifDto expectedCifDto = expectedDtoMethod();

        String getCif = getAccountDetails(accountNumber);

        Map<String, String> mapData = getStringStringMap(accountNumber, getCif);

        // Log the contents of the mapData object
        log.info("mapData: " + mapData);

        try {
            cifDto.setCifType(mapData.get("cifType"));
            cifDto.setCifId(mapData.get("cifId"));
            cifDto.setKycLevel(Integer.parseInt(mapData.get("kycLevel")));
            cifDto.setFreezeCode(mapData.get("freezeCode"));
            cifDto.setAccountSegment(mapData.get("accountSegment"));
            cifDto.setCurrency(mapData.get("currency"));
        } catch (Exception e) {
            log.info(e.toString());
            throw new MyCustomizedException("error parsing data");
        }

        if (mapData.get("cifId").equalsIgnoreCase("null") || mapData.get("cifId") == null || mapData.get("cifId").equalsIgnoreCase("null")) {
            String message = String.format("account number %s does not exist", accountNumber);
            throw new MyCustomizedException(message);
        }
        log.info("cifDto: " + cifDto);

        // Use assertions to compare the actual and expected values
//        assertEquals(expectedCifDto.getCifType(), cifDto.getCifType());
//        assertEquals(expectedCifDto.getCifId(), cifDto.getCifId());
//        assertEquals(expectedCifDto.getKycLevel(), cifDto.getKycLevel());
//        assertEquals(expectedCifDto.getFreezeCode(), cifDto.getFreezeCode());
//        assertEquals(expectedCifDto.getAccountSegment(), cifDto.getAccountSegment());
//        assertEquals(expectedCifDto.getCurrency(), cifDto.getCurrency());
//        return cifDto;
    }

    private CifDto expectedDtoMethod() {

        CifDto expectedCifDto = new CifDto();
        expectedCifDto.setCifType("Individual");
        expectedCifDto.setCifId("99999999999999");
        expectedCifDto.setKycLevel(3);
        expectedCifDto.setFreezeCode("Active");
        expectedCifDto.setAccountSegment("Savings");
        expectedCifDto.setCurrency("NGN");
        return expectedCifDto;
    }

    private static Map<String, String> getStringStringMap(String accountNumber, String getCif) {
        Map<String, String> mapData;

        if (getCif == null) {
            String message = String.format("account number %s cannot be validated", accountNumber);
            throw new MyCustomizedException(message);
        }

        String[] separateVariable = getCif.split("[|]", 20);

        String[] keys = {"active", "name", "freezeCode", "actLimit", "kycLevel", "bvn", "cifId", "cifType", "Schmcode", "balance", "currency", "accountSegment"};

        mapData = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            mapData.put(keys[i], separateVariable[i]);
        }
        return mapData;
    }

    private String getAccountDetails(String accountNumber) {
        if (accountNumber.equals("555555")) {
            return "Y|xxxxx xxxxx xxxxx| |99999999999999.99|3|qqqqqqq|tttttt|RET|pppppp|916827250.09|NGN|ppppp-staff";
        } else if (accountNumber.equals("null")) {
            return null;
        } else {
            return "null|null|null|null|null|null|null|null|null|null|null|null";
        }
    }
}
