/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.core;

import co.rsk.Start;
import co.rsk.config.RskSystemProperties;
import co.rsk.core.bc.BlockChainImpl;
import co.rsk.core.bc.PendingStateImpl;
import co.rsk.metrics.HashRateCalculator;
import co.rsk.mine.MinerClient;
import co.rsk.mine.MinerServer;
import co.rsk.net.*;
import co.rsk.net.eth.RskWireProtocol;
import co.rsk.net.handler.TxHandler;
import co.rsk.net.handler.TxHandlerImpl;
import co.rsk.net.sync.SyncConfiguration;
import co.rsk.rpc.Web3RskImpl;
import co.rsk.rpc.modules.eth.*;
import co.rsk.rpc.modules.personal.PersonalModule;
import co.rsk.rpc.modules.personal.PersonalModuleWalletDisabled;
import co.rsk.rpc.modules.personal.PersonalModuleWalletEnabled;
import co.rsk.scoring.PeerScoringManager;
import co.rsk.scoring.PunishmentParameters;
import co.rsk.validators.BlockValidator;
import co.rsk.validators.ProofOfWorkRule;
import org.ethereum.config.SystemProperties;
import org.ethereum.core.Blockchain;
import org.ethereum.core.PendingState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.db.ReceiptStore;
import org.ethereum.listener.CompositeEthereumListener;
import org.ethereum.listener.EthereumListener;
import org.ethereum.manager.AdminInfo;
import org.ethereum.net.EthereumChannelInitializerFactory;
import org.ethereum.net.MessageQueue;
import org.ethereum.net.NodeManager;
import org.ethereum.net.client.ConfigCapabilities;
import org.ethereum.net.client.PeerClient;
import org.ethereum.net.eth.handler.EthHandlerFactory;
import org.ethereum.net.eth.handler.EthHandlerFactoryImpl;
import org.ethereum.net.message.StaticMessages;
import org.ethereum.net.p2p.P2pHandler;
import org.ethereum.net.rlpx.HandshakeHandler;
import org.ethereum.net.rlpx.MessageCodec;
import org.ethereum.net.server.*;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.sync.SyncPool;
import org.ethereum.vm.program.invoke.ProgramInvokeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("org.ethereum")
public class RskFactory {

    private static final Logger logger = LoggerFactory.getLogger("general");

    @Bean
    public PeerScoringManager getPeerScoringManager(SystemProperties config) {
        int nnodes = config.scoringNumberOfNodes();

        long nodePunishmentDuration = config.scoringNodesPunishmentDuration();
        int nodePunishmentIncrement = config.scoringNodesPunishmentIncrement();
        long nodePunhishmentMaximumDuration = config.scoringNodesPunishmentMaximumDuration();

        long addressPunishmentDuration = config.scoringAddressesPunishmentDuration();
        int addressPunishmentIncrement = config.scoringAddressesPunishmentIncrement();
        long addressPunishmentMaximunDuration = config.scoringAddressesPunishmentMaximumDuration();

        return new PeerScoringManager(nnodes, new PunishmentParameters(nodePunishmentDuration, nodePunishmentIncrement,
                nodePunhishmentMaximumDuration), new PunishmentParameters(addressPunishmentDuration, addressPunishmentIncrement, addressPunishmentMaximunDuration));
    }

    @Bean
    public NodeBlockProcessor getNodeBlockProcessor(Blockchain blockchain, BlockStore blockStore,
                                                    BlockNodeInformation blockNodeInformation, BlockSyncService blockSyncService, SyncConfiguration syncConfiguration) {
        return new NodeBlockProcessor(blockStore, blockchain, blockNodeInformation, blockSyncService, syncConfiguration);
    }

    @Bean
    public SyncProcessor getSyncProcessor(RskSystemProperties config,
                                          Blockchain blockchain,
                                          BlockSyncService blockSyncService,
                                          PeerScoringManager peerScoringManager,
                                          SyncConfiguration syncConfiguration,
                                          DifficultyCalculator difficultyCalculator,
                                          ProofOfWorkRule proofOfWorkRule) {

        // TODO(lsebrie): add new BlockCompositeRule(new ProofOfWorkRule(), blockTimeStampValidationRule, new ValidGasUsedRule());
        return new SyncProcessor(config, blockchain, blockSyncService, peerScoringManager, syncConfiguration, proofOfWorkRule, difficultyCalculator);
    }

    @Bean
    public BlockSyncService getBlockSyncService(Blockchain blockchain,
                                                BlockStore store,
                                                BlockNodeInformation nodeInformation,
                                                SyncConfiguration syncConfiguration) {
            return new BlockSyncService(store, blockchain, nodeInformation, syncConfiguration);
    }

    @Bean
    public SyncPool getSyncPool(@Qualifier("compositeEthereumListener") EthereumListener ethereumListener,
                                Blockchain blockchain,
                                RskSystemProperties config,
                                NodeManager nodeManager) {
        return new SyncPool(ethereumListener, blockchain, config, nodeManager);
    }

    @Bean
    public TxHandler getTxHandler(RskSystemProperties config, CompositeEthereumListener compositeEthereumListener, Repository repository, Blockchain blockchain) {
        return new TxHandlerImpl(config, compositeEthereumListener, repository, blockchain);
    }

    @Bean
    public Start.Web3Factory getWeb3Factory(Rsk rsk,
                                            Blockchain blockchain,
                                            PendingState pendingState,
                                            RskSystemProperties config,
                                            MinerClient minerClient,
                                            MinerServer minerServer,
                                            PersonalModule personalModule,
                                            EthModule ethModule,
                                            ChannelManager channelManager,
                                            Repository repository,
                                            PeerScoringManager peerScoringManager,
                                            NetworkStateExporter networkStateExporter,
                                            org.ethereum.db.BlockStore blockStore,
                                            PeerServer peerServer,
                                            BlockProcessor nodeBlockProcessor,
                                            HashRateCalculator hashRateCalculator,
                                            ConfigCapabilities configCapabilities) {
        return () -> new Web3RskImpl(rsk, blockchain, pendingState, config, minerClient, minerServer, personalModule, ethModule, channelManager, repository, peerScoringManager, networkStateExporter, blockStore, peerServer, nodeBlockProcessor, hashRateCalculator, configCapabilities);
    }

    @Bean
    public BlockChainImpl getBlockchain(org.ethereum.core.Repository repository,
                                        org.ethereum.db.BlockStore blockStore,
                                        ReceiptStore receiptStore,
                                        PendingState pendingState,
                                        @Qualifier("compositeEthereumListener") EthereumListener listener,
                                        AdminInfo adminInfo,
                                        BlockValidator blockValidator,
                                        RskSystemProperties config) {
        return new BlockChainImpl(
                config,
                repository,
                blockStore,
                receiptStore,
                pendingState,
                listener,
                adminInfo,
                blockValidator
        );
    }

    @Bean
    public PendingState getPendingState(org.ethereum.db.BlockStore blockStore,
                                        ReceiptStore receiptStore,
                                        org.ethereum.core.Repository repository,
                                        RskSystemProperties config,
                                        ProgramInvokeFactory programInvokeFactory,
                                        @Qualifier("compositeEthereumListener") EthereumListener listener) {
        return new PendingStateImpl(
                blockStore,
                receiptStore,
                listener,
                programInvokeFactory,
                repository,
                config
        );
    }

    @Bean
    public SyncPool.PeerClientFactory getPeerClientFactory(SystemProperties config,
                                                           @Qualifier("compositeEthereumListener") EthereumListener ethereumListener,
                                                           EthereumChannelInitializerFactory ethereumChannelInitializerFactory) {
        return () -> new PeerClient(config, ethereumListener, ethereumChannelInitializerFactory);
    }

    @Bean
    public EthereumChannelInitializerFactory getEthereumChannelInitializerFactory(ChannelManager channelManager, EthereumChannelInitializer.ChannelFactory channelFactory) {
        return remoteId -> new EthereumChannelInitializer(remoteId, channelManager, channelFactory);
    }

    @Bean
    public EthereumChannelInitializer.ChannelFactory getChannelFactory(RskSystemProperties config,
                                                                       @Qualifier("compositeEthereumListener") EthereumListener ethereumListener,
                                                                       ConfigCapabilities configCapabilities,
                                                                       NodeManager nodeManager,
                                                                       EthHandlerFactory ethHandlerFactory,
                                                                       StaticMessages staticMessages,
                                                                       PeerScoringManager peerScoringManager) {
        return () -> {
            HandshakeHandler handshakeHandler = new HandshakeHandler(config, peerScoringManager);
            MessageQueue messageQueue = new MessageQueue();
            P2pHandler p2pHandler = new P2pHandler(config, ethereumListener, configCapabilities);
            MessageCodec messageCodec = new MessageCodec(ethereumListener, config);
            return new Channel(config, messageQueue, p2pHandler, messageCodec, handshakeHandler, nodeManager, ethHandlerFactory, staticMessages);
        };
    }

    @Bean
    public EthHandlerFactoryImpl.RskWireProtocolFactory getRskWireProtocolFactory(PeerScoringManager peerScoringManager,
                                                                                  MessageHandler messageHandler,
                                                                                  Blockchain blockchain,
                                                                                  RskSystemProperties config,
                                                                                  CompositeEthereumListener ethereumListener){
        return () -> new RskWireProtocol(config, peerScoringManager, messageHandler, blockchain, ethereumListener);
    }

    @Bean
    public PeerServer getPeerServer(SystemProperties config,
                                    @Qualifier("compositeEthereumListener") EthereumListener ethereumListener,
                                    EthereumChannelInitializerFactory ethereumChannelInitializerFactory) {
        return new PeerServerImpl(config, ethereumListener, ethereumChannelInitializerFactory);
    }

    @Bean
    public Wallet getWallet(RskSystemProperties config) {
        if (!config.isWalletEnabled()) {
            logger.info("Local wallet disabled");
            return null;
        }

        logger.info("Local wallet enabled");
        KeyValueDataSource ds = new LevelDbDataSource(config, "wallet");
        ds.init();
        return new Wallet(ds);
    }

    @Bean
    public PersonalModule getPersonalModuleWallet(RskSystemProperties config, Rsk rsk, Wallet wallet, PendingState pendingState) {
        if (wallet == null) {
            return new PersonalModuleWalletDisabled();
        }

        return new PersonalModuleWalletEnabled(config, rsk, wallet, pendingState);
    }

    @Bean
    public EthModuleWallet getEthModuleWallet(RskSystemProperties config, Rsk rsk, Wallet wallet, PendingState pendingState) {
        if (wallet == null) {
            return new EthModuleWalletDisabled();
        }

        return new EthModuleWalletEnabled(config, rsk, wallet, pendingState);
    }

    @Bean
    public EthModuleSolidity getEthModuleSolidity(RskSystemProperties config) {
        try {
            return new EthModuleSolidityEnabled(new SolidityCompiler(config));
        } catch (RuntimeException e) {
            // the only way we currently have to check if Solidity is available is catching this exception
            logger.debug("Solidity compiler unavailable", e);
            return new EthModuleSolidityDisabled();
        }
    }

    @Bean
    public SyncConfiguration getSyncConfiguration(RskSystemProperties config) {
        int expectedPeers = config.getExpectedPeers();
        int timeoutWaitingPeers = config.getTimeoutWaitingPeers();
        int timeoutWaitingRequest = config.getTimeoutWaitingRequest();
        int expirationTimePeerStatus = config.getExpirationTimePeerStatus();
        int maxSkeletonChunks = config.getMaxSkeletonChunks();
        int chunkSize = config.getChunkSize();
        return new SyncConfiguration(expectedPeers, timeoutWaitingPeers, timeoutWaitingRequest,
                expirationTimePeerStatus, maxSkeletonChunks, chunkSize);
    }

    @Bean
    public BlockStore getBlockStore(){
        return new BlockStore();
    }
}
