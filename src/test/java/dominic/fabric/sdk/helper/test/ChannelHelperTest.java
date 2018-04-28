package dominic.fabric.sdk.helper.test;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import dominic.common.base.ResultDTO;
import dominic.fabric.sdk.helper.ChaincodeHelper;
import dominic.fabric.sdk.helper.ChannelHelper;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ChannelHelperTest {

    private String parentPath = "src/test/resources/sdkintegration/";

    private boolean createFooChannel = true;
    private boolean install = true;
    private boolean instantiate = true;
    private boolean transfer = true;

    @Test
    public void test() throws Exception {
        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(parentPath, "network-configs/network-config.yaml"));

        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        String orgName = "Org1";
        NetworkConfig.UserInfo peerAdmin = networkConfig.getPeerAdmin(orgName);
        client.setUserContext(peerAdmin);

        String channelName = "foo";
        Channel fooChannel;
        if (createFooChannel) {
            fooChannel = ChannelHelper.createNewChannel(networkConfig, client, channelName,
                    new File(parentPath, "e2e-2Orgs/v1.1/foo.tx"), orgName);
        } else {
            fooChannel = ChannelHelper.resumeChannel(networkConfig, client, channelName);
        }

        //chaincode
        ChaincodeHelper chaincodeHelper = ChaincodeHelper.getHelper(fooChannel);

        //install chaincode
        String ccName = "example_cc_go";
        String ccVersion = "1";
        String ccPath = "github.com/example_cc";
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(ccName)
                .setVersion(ccVersion).setPath(ccPath).build();
        if (install) {
            InstallProposalRequest installProposalRequest = getInstallProposalRequest(client, ccVersion, chaincodeID);
            ResultDTO<Collection<ProposalResponse>> installResultDTO = chaincodeHelper.install(installProposalRequest, client);
            if (!installResultDTO.isSuccess()) {
                log.error("install fail: {}", installResultDTO.getMessage());
                return;
            }
        }

        if (instantiate) {
            //instantiate chaincode
            InstantiateProposalRequest instantiateProposalRequest = getInstantiateProposalRequest(client, chaincodeID);
            ResultDTO<BlockEvent.TransactionEvent> eventResultDTO = chaincodeHelper.instantiate(instantiateProposalRequest);
            if (!eventResultDTO.isSuccess()) {
                log.error("install fail: {}", eventResultDTO.getMessage());
                return;
            }

            BlockEvent.TransactionEvent event = eventResultDTO.getModel();
            log.info("========= instantiate chaincode's transactionId: {}", event.getTransactionID());
        }

        if (transfer) {
            //transfer
            TransactionProposalRequest transactionProposalRequest = getTransactionProposalRequest(client, chaincodeID);
            ResultDTO<BlockEvent.TransactionEvent> transactResultDTO = chaincodeHelper.transact(transactionProposalRequest);
            if (!transactResultDTO.isSuccess()) {
                log.error("transact fail: {}", transactResultDTO.getMessage());
                return;
            }

            BlockEvent.TransactionEvent transactionEvent = transactResultDTO.getModel();
            log.info("========= move()'s transactionId: {}", transactionEvent.getTransactionID());
        }

        //query chaincode
        QueryByChaincodeRequest queryByChaincodeRequest = getQueryByChaincodeRequest(client, chaincodeID);
//        ResultDTO<Collection<ProposalResponse>> queryResultDTO = chaincodeHelper.queryByChaincode(queryByChaincodeRequest);
//        log.info("======== queryByChaincode: {}", JSON.toJSONString(queryResultDTO.getModel())); //to json error

        log.info("============ query split ================");
        ResultDTO<Collection<String>> queryInfosResultDTO = chaincodeHelper.queryInfoByChaincode(queryByChaincodeRequest);
        log.info("======== queryInfoByChaincode: {}", JSON.toJSONString(queryInfosResultDTO.getModel()));

        log.info("============ blockInfo split ================");
        //blockInfo

    }

    private QueryByChaincodeRequest getQueryByChaincodeRequest(HFClient client, ChaincodeID chaincodeID) {
        QueryByChaincodeRequest queryByChaincodeRequest = client.newQueryProposalRequest();
        queryByChaincodeRequest.setArgs("b");
        queryByChaincodeRequest.setFcn("query");
        queryByChaincodeRequest.setChaincodeID(chaincodeID);
        return queryByChaincodeRequest;
    }

    private TransactionProposalRequest getTransactionProposalRequest(HFClient client, ChaincodeID chaincodeID) {
        TransactionProposalRequest transactionProposalRequest = client.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(chaincodeID);
        transactionProposalRequest.setChaincodeLanguage(TransactionRequest.Type.GO_LANG);
        transactionProposalRequest.setFcn("move");
//        transactionProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
        transactionProposalRequest.setArgs("a", "b", "100");
        return transactionProposalRequest;
    }

    private InstallProposalRequest getInstallProposalRequest(HFClient client, String ccVersion, ChaincodeID chaincodeID) throws InvalidArgumentException {
        InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(chaincodeID);
        installProposalRequest.setChaincodeSourceLocation(new File(parentPath, "gocc/sample1"));
        installProposalRequest.setChaincodeVersion(ccVersion);
        installProposalRequest.setChaincodeLanguage(TransactionRequest.Type.GO_LANG);
        return installProposalRequest;
    }

    private InstantiateProposalRequest getInstantiateProposalRequest(HFClient client, ChaincodeID chaincodeID) throws InvalidArgumentException, IOException, ChaincodeEndorsementPolicyParseException {
        InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
//        instantiateProposalRequest.setProposalWaitTime(testConfig.getProposalWaitTime());
        instantiateProposalRequest.setChaincodeID(chaincodeID);
        instantiateProposalRequest.setFcn("init");
        instantiateProposalRequest.setArgs("a", "500", "b", "200");

        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(StandardCharsets.UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(StandardCharsets.UTF_8));
        instantiateProposalRequest.setTransientMap(tm);
        ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
        chaincodeEndorsementPolicy.fromYamlFile(new File(parentPath, "chaincodeendorsementpolicy.yaml"));
        instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);
        return instantiateProposalRequest;
    }
}
