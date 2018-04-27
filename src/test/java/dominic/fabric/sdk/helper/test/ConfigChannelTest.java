package dominic.fabric.sdk.helper.test;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import dominic.common.base.ResultDTO;
import dominic.fabric.sdk.helper.ChaincodeHelper;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
public class ConfigChannelTest {

    private String parentPath = "src/test/resources/";

    private boolean createFooChannel = false;
    private boolean install = false;
    private boolean instantiate = false;
    private boolean transfer = false;

    @Test
    public void test() throws Exception {
        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(parentPath, "network-configs/network-config.yaml"));

        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        NetworkConfig.UserInfo peerAdmin = networkConfig.getPeerAdmin("Org1");
        client.setUserContext(peerAdmin);

        String channelName = "foo";
        HFClient loadChannelClient = HFClient.createNewInstance();
        loadChannelClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        loadChannelClient.setUserContext(peerAdmin);
        Channel configFooChannel = loadChannelClient.loadChannelFromConfig(channelName, networkConfig); //userContext can't be null
        if (configFooChannel == null) {
            throw new RuntimeException("Channel " + channelName + " is not defined in the config file!");
        }

        Channel fooChannel;
        if (createFooChannel) {
            fooChannel = constructChannel(networkConfig, client, channelName, configFooChannel);
        } else {
            fooChannel = client.loadChannelFromConfig(channelName, networkConfig);
            fooChannel.initialize();
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

    private Channel constructChannel(NetworkConfig networkConfig, HFClient client, String channelName, Channel configFooChannel) throws NetworkConfigurationException, InvalidArgumentException, IOException, TransactionException, ProposalException {
        List<Orderer> orderers = Lists.newArrayList();
        List<Peer> peers = Lists.newArrayList();
        List<EventHub> eventHubs = Lists.newArrayList();

        //copy
        for (Orderer orderer : configFooChannel.getOrderers()) {
            orderers.add(client.newOrderer(orderer.getName(), orderer.getUrl(), orderer.getProperties()));
        }
        for (Peer peer : configFooChannel.getPeers()) {
            peers.add(client.newPeer(peer.getName(), peer.getUrl(), peer.getProperties()));
        }
        for (EventHub eventHub : configFooChannel.getEventHubs()) {
            eventHubs.add(client.newEventHub(eventHub.getName(), eventHub.getUrl(), eventHub.getProperties()));
        }

        //construct a new channel by the configChannel
        NetworkConfig.UserInfo peerAdmin = networkConfig.getPeerAdmin("Org1");
        client.setUserContext(peerAdmin);

        Orderer anOrderer = orderers.iterator().next();
        orderers.remove(anOrderer);
        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(parentPath, "e2e-2Orgs/v1.1/foo.tx"));
        //Create channel that has only one signer that is this orgs peer admin. If channel creation policy needed more signature they would need to be added too.
        Channel newChannel = client.newChannel(channelName, anOrderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, peerAdmin));

        for (Orderer orderer : orderers) { //add remaining orderers if any.
            newChannel.addOrderer(orderer);
        }

        for (Peer peer : peers) {
            newChannel.joinPeer(peer, Channel.PeerOptions.createPeerOptions()); //Default is all roles.
        }

        for (EventHub eventHub : eventHubs) {
            newChannel.addEventHub(eventHub);
        }

        newChannel.initialize();
        return newChannel;
    }
}
