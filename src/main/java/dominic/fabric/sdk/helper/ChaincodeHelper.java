package dominic.fabric.sdk.helper;

import com.google.common.base.Preconditions;
import dominic.common.base.ResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ChaincodeHelper {

    private Channel channel;

    private ChaincodeHelper(Channel channel) {
        Preconditions.checkNotNull(channel, "channel不能为null");
        this.channel = channel;
    }

    public static ChaincodeHelper getHelper(Channel channel) {
        return new ChaincodeHelper(channel);
    }

    public ResultDTO<Collection<ProposalResponse>> install(InstallProposalRequest request, HFClient client) {
        Preconditions.checkNotNull(request, "InstallProposalRequest不能为null");
        Preconditions.checkNotNull(client, "HFClient不能为null");

        Collection<ProposalResponse> responses;
        try {
            responses = client.sendInstallProposal(request, channel.getPeers());
        } catch (ProposalException | InvalidArgumentException e) {
            log.error("install chaincode fail:", e);
            return ResultDTO.failed(e.getMessage());
        }

        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();

        for (ProposalResponse response : responses) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                log.debug(String.format("Successful install proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName()));
                successful.add(response);
            } else {
                failed.add(response);
            }
        }

        log.debug(String.format("Received %d install proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size()));

        if (failed.size() > 0) {
            ProposalResponse first = failed.iterator().next();
            String message = "Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage();
//            log.error("install chaincode fail:", message);
            return ResultDTO.failedWith(failed, message);
        }

        return ResultDTO.succeedWith(successful);
    }

    public ResultDTO<BlockEvent.TransactionEvent> instantiate(InstantiateProposalRequest request) {
        return instantiate(request, Channel.TransactionOptions.createTransactionOptions().orderers(channel.getOrderers()));
    }
    public ResultDTO<BlockEvent.TransactionEvent> instantiate(InstantiateProposalRequest request, Channel.TransactionOptions options) {
        Preconditions.checkNotNull(request, "InstantiateProposalRequest不能为null");

        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        Collection<ProposalResponse> responses;
        log.debug("Sending instantiateProposalRequest to all peers...");
        try {
            responses = channel.sendInstantiationProposal(request);
        } catch (ProposalException | InvalidArgumentException e) {
            String errorMessagePrefix = "instantiate chaincode fail when Sending instantiateProposalRequest to all peers: ";
            log.error(errorMessagePrefix, e);
            return ResultDTO.failed(errorMessagePrefix + e.getMessage());
        }

        ResultDTO<BlockEvent.TransactionEvent> message = handleProposalResponse(successful, failed, responses);
        if (message != null) return message;

        log.debug("Sending instantiateTransaction to orderer...");
        return sendTransaction(options, successful);
    }

    private ResultDTO<BlockEvent.TransactionEvent> sendTransaction(Channel.TransactionOptions options, Collection<ProposalResponse> successful) {
        try {
            BlockEvent.TransactionEvent transactionEvent = channel.sendTransaction(successful, options).get(30, TimeUnit.SECONDS);//todo timeout可配置
            return ResultDTO.succeedWith(transactionEvent);
        } catch (Exception e) {
            String errorMessagePrefix = "transaction fail when Sending transaction to orderer: ";
            log.error(errorMessagePrefix, e);
            return ResultDTO.failed(errorMessagePrefix + e.getMessage());
        }
    }

    private ResultDTO<BlockEvent.TransactionEvent> handleProposalResponse(Collection<ProposalResponse> successful, Collection<ProposalResponse> failed, Collection<ProposalResponse> responses) {
        for (ProposalResponse response : responses) {
            if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
                log.debug(String.format("Successful proposal response Txid: %s from peer %s", response.getTransactionID(), response.getPeer().getName()));
                successful.add(response);
            } else {
                failed.add(response);
            }
        }
        log.debug(String.format("Received %d proposal responses. Successful+verified: %d . Failed: %d", responses.size(), successful.size(), failed.size()));
        //todo 注意chaincode endorsement policy. fabric和sdk好像都没有做policy的处理
        if (failed.size() > 0) {
            ProposalResponse first = failed.iterator().next();
            String message = "Not enough endorsers for this proposal: " + successful.size() + ". endorser failed with " + first.getMessage() + ". Was verified:" + first.isVerified();
//            log.error("install chaincode fail:", message);
            return ResultDTO.failed(message);
        }
        return null;
    }

    public ResultDTO<BlockEvent.TransactionEvent> transact(TransactionProposalRequest request) {
        return transact(request, Channel.TransactionOptions.createTransactionOptions().orderers(channel.getOrderers()));
    }
    public ResultDTO<BlockEvent.TransactionEvent> transact(TransactionProposalRequest request, Channel.TransactionOptions options) {
        Preconditions.checkNotNull(request, "TransactionProposalRequest不能为null");

        Collection<ProposalResponse> successful = new LinkedList<>();
        Collection<ProposalResponse> failed = new LinkedList<>();
        Collection<ProposalResponse> responses;
        log.debug("Sending transactionProposalRequest to all peers...");
        try {
            responses = channel.sendTransactionProposal(request);
        } catch (ProposalException | InvalidArgumentException e) {
            String errorMessagePrefix = "transact fail when Sending ProposalRequest to all peers: ";
            log.error(errorMessagePrefix, e);
            return ResultDTO.failed(errorMessagePrefix + e.getMessage());
        }

        ResultDTO<BlockEvent.TransactionEvent> message = handleProposalResponse(successful, failed, responses);
        if (message != null) return message;

        log.debug("Sending chaincode transaction to orderer...");
        return sendTransaction(options, successful);
    }

    public ResultDTO<Collection<ProposalResponse>> queryByChaincode(QueryByChaincodeRequest request) {
        Preconditions.checkNotNull(request, "QueryByChaincodeRequest不能为null");

        Collection<ProposalResponse> responses;
        try {
            responses = channel.queryByChaincode(request);
        } catch (Exception e) {
            String message = String.format("queryByChaincode异常， request参数：%s, 查询方法：%s  ", request.getArgs(), request.getFcn());
            log.error(message, e);
            return ResultDTO.failed(message + e.getMessage());
        }
        return ResultDTO.succeedWith(responses);
    }

    public ResultDTO<Collection<String>> queryInfoByChaincode(QueryByChaincodeRequest request) {
        ResultDTO<Collection<ProposalResponse>> resultDTO = queryByChaincode(request);
        if (!resultDTO.isSuccess()) {
            return ResultDTO.failed(resultDTO.getMessage());
        }

        Collection<String> collect = resultDTO.getModel().stream().map(response -> {
            if (!response.isVerified() || response.getStatus() != ChaincodeResponse.Status.SUCCESS) {
                return String.format("查询节点：%s 失败，status：%s; messages: %s; Was verified: %s",
                        response.getPeer().getName(), response.getStatus(), response.getMessage(), response.isVerified());
            } else {
                return response.getProposalResponse().getResponse().getPayload().toStringUtf8();
            }
        }).collect(Collectors.toList());
        return ResultDTO.succeedWith(collect);
    }
}
