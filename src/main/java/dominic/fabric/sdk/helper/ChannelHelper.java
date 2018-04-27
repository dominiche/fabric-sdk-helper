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


        Orderer anOrderer = orderers.iterator().next();
        orderers.remove(anOrderer);
        ChannelConfiguration channelConfiguration = new ChannelConfiguration(channelTxFile);
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

    public static Channel resumeChannel(NetworkConfig networkConfig, HFClient client, String channelName) {
        Preconditions.checkNotNull(networkConfig);
        Preconditions.checkNotNull(client);
        Preconditions.checkNotNull(channelName);

        try {
            Channel channel = client.loadChannelFromConfig(channelName, networkConfig);
            if (channel == null) {
                throw new RuntimeException("Channel " + channelName + " is not defined in the config file!");
            }
            channel.initialize();
            return channel;
        } catch (Exception e) {
            throw new RuntimeException(String.format("resumeChannel %s fail:", channelName), e);
        }
    }
}
