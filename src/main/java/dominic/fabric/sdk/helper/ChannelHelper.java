package dominic.fabric.sdk.helper;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.io.File;
import java.util.List;

@Slf4j
public class ChannelHelper {

    public static Channel createNewChannel(NetworkConfig networkConfig, HFClient client, String channelName, File channelTxFile, String orgName) throws Exception {
        Preconditions.checkNotNull(networkConfig);
        Preconditions.checkNotNull(client);
        Preconditions.checkNotNull(channelTxFile);
        Preconditions.checkNotNull(channelName);
        Preconditions.checkNotNull(orgName);


        //construct a new channel by the configChannel
        NetworkConfig.UserInfo peerAdmin = networkConfig.getPeerAdmin(orgName);

        HFClient loadChannelClient = HFClient.createNewInstance();
        loadChannelClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());
        loadChannelClient.setUserContext(peerAdmin);
        Channel configFooChannel = loadChannelClient.loadChannelFromConfig(channelName, networkConfig); //userContext can't be null
        if (configFooChannel == null) {
            throw new RuntimeException("Channel " + channelName + " is not defined in the config file!");
        }


        List<Orderer> orderers = Lists.newArrayList();
        for (Orderer orderer : configFooChannel.getOrderers()) {
            orderers.add(client.newOrderer(orderer.getName(), orderer.getUrl(), orderer.getProperties()));
        }

        Orderer anOrderer = orderers.iterator().next();
        orderers.remove(anOrderer);
        ChannelConfiguration channelConfiguration = new ChannelConfiguration(channelTxFile);
        //Create channel that has only one signer that is this orgs peer admin. If channel creation policy needed more signature they would need to be added too.
        Channel newChannel = client.newChannel(channelName, anOrderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, peerAdmin));

        for (Orderer orderer : orderers) { //add remaining orderers if any.
            newChannel.addOrderer(orderer);
        }

        for (Peer peer : configFooChannel.getPeers()) {
            Peer newPeer = client.newPeer(peer.getName(), peer.getUrl(), peer.getProperties());
            newChannel.joinPeer(newPeer, configFooChannel.getPeersOptions(peer)); //Default is all roles.
        }

        for (EventHub eventHub : configFooChannel.getEventHubs()) {
            EventHub newEventHub = client.newEventHub(eventHub.getName(), eventHub.getUrl(), eventHub.getProperties());
            newChannel.addEventHub(newEventHub);
        }

        newChannel.initialize();
        return newChannel;
    }

    public static Channel resumeChannel(NetworkConfig networkConfig, HFClient client, String channelName) throws Exception {
        Preconditions.checkNotNull(networkConfig);
        Preconditions.checkNotNull(client);
        Preconditions.checkNotNull(channelName);

        Channel channel = client.loadChannelFromConfig(channelName, networkConfig);
        if (channel == null) {
            throw new Exception("Channel " + channelName + " is not defined in the config file!");
        }
        channel.initialize();
        return channel;
    }
}
