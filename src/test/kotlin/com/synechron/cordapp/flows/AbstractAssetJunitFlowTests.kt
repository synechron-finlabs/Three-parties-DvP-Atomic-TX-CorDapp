package com.synechron.cordapp.flows

import com.synechron.cordapp.buyer.flows.AssetSettlementResponderFlow
import com.synechron.cordapp.clearinghouse.flows.AssetSettlementInitiatorFlow
import com.synechron.cordapp.buyer.flows.ConfirmAssetTransferRequestInitiatorFlow
import com.synechron.cordapp.buyer.flows.CreateAssetTransferRequestResponderFlow
import com.synechron.cordapp.seller.flows.ConfirmAssetTransferRequestHandlerFlow
import com.synechron.cordapp.seller.flows.CreateAssetStateFlow
import com.synechron.cordapp.seller.flows.CreateAssetTransferRequestInitiatorFlow
import com.synechron.cordapp.state.Asset
import com.synechron.cordapp.obligation.exception.StateNotFoundOnVaultException
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import java.util.*
import kotlin.test.assertEquals

/**
 * A base class to reduce the boilerplate when writing Asset flow tests.
 */
abstract class AbstractAssetJunitFlowTests {
    lateinit var network: MockNetwork
    lateinit var lenderOfSecurity: StartedMockNode
    lateinit var lenderOfCash: StartedMockNode
    lateinit var globalCustodian: StartedMockNode
    lateinit var lenderOfSecurityParty: Party
    lateinit var lenderOfCashParty: Party
    lateinit var custodianParty: Party

    protected val cusip = "CUSIP123"

    @Before
    fun setup() {
        network = MockNetwork(listOf("com.synechron.cordapp", "net.corda.finance", "net.corda.finance.schemas"), threadPerNode = true)

        lenderOfSecurity = network.createNode()
        lenderOfCash = network.createNode()
        globalCustodian = network.createNode()
        val nodes = listOf(lenderOfSecurity, lenderOfCash, globalCustodian)

        lenderOfCash.registerInitiatedFlow(CreateAssetTransferRequestResponderFlow::class.java)
        lenderOfCash.registerInitiatedFlow(AssetSettlementResponderFlow::class.java)

        lenderOfSecurity.registerInitiatedFlow(ConfirmAssetTransferRequestHandlerFlow::class.java)
        lenderOfSecurity.registerInitiatedFlow(com.synechron.cordapp.seller.flows.AssetSettlementResponderFlow::class.java)

        globalCustodian.registerInitiatedFlow(com.synechron.cordapp.clearinghouse.flows.ConfirmAssetTransferRequestResponderFlow::class.java)

        lenderOfSecurityParty = lenderOfSecurity.info.chooseIdentity()
        lenderOfCashParty = lenderOfCash.info.chooseIdentity()
        custodianParty = globalCustodian.info.chooseIdentity()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    protected fun createAsset(owner: StartedMockNode,
                              cusip: String,
                              assetName: String,
                              purchaseCost: Amount<Currency>
    ): SignedTransaction {
        val flow = CreateAssetStateFlow.Initiator(cusip, assetName, purchaseCost)
        return owner.startFlow(flow).getOrThrow()
    }

    protected fun createAsset(): Asset {
        val stx = createAsset(lenderOfSecurity, cusip, "US BOND", DOLLARS(1000))
        network.waitQuiescent()

        return lenderOfSecurity.transaction {
            val asset = lenderOfSecurity.services.loadState(stx.tx.outRef<Asset>(0).ref).data as Asset
            assertEquals(asset.cusip, cusip)
            asset
        }
    }

    protected fun createAssetTransferRequest(lenderOfSecurity: StartedMockNode,
                                             lenderOfCash: Party,
                                             cusip: String): SignedTransaction {
        val flow = CreateAssetTransferRequestInitiatorFlow(cusip, lenderOfCash)
        return lenderOfSecurity.startFlow(flow).getOrThrow()
    }

    protected fun confirmAssetTransferRequest(lenderOfCash: StartedMockNode,
                                              custodian: Party,
                                              linearId: UniqueIdentifier): SignedTransaction {
        val flow = ConfirmAssetTransferRequestInitiatorFlow(linearId, custodian)
        return lenderOfCash.startFlow(flow).getOrThrow()
    }

    protected fun settleAssetTransferRequest(custodianNode: StartedMockNode,
                                             linearId: UniqueIdentifier): SignedTransaction {
        val flow = AssetSettlementInitiatorFlow(linearId)
        return custodianNode.startFlow(flow).getOrThrow()
    }

    protected fun selfIssueCash(node: StartedMockNode,
                                amount: Amount<Currency>): SignedTransaction {
        val notary = node.services.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw IllegalStateException("Could not find a notary.")
        val issueRef = OpaqueBytes.of(0)
        val issueRequest = CashIssueFlow.IssueRequest(amount, issueRef, notary)
        val flow = CashIssueFlow(issueRequest)
        return node.startFlow(flow).getOrThrow().stx
    }

    protected fun resolveIdentity(node: StartedMockNode,
                                  anonymousParty: AbstractParty): Party {
        return node.services.identityService.requireWellKnownPartyFromAnonymous(anonymousParty)
    }

    protected fun <T : ContractState> getStateByLinearId(linearId: UniqueIdentifier, clazz: Class<T>, mockNode: StartedMockNode): StateAndRef<T> {
        val queryCriteria = QueryCriteria.LinearStateQueryCriteria(null,
                listOf(linearId), Vault.StateStatus.UNCONSUMED, null)
        return mockNode.transaction {
            mockNode.services.vaultService.queryBy(clazz, queryCriteria).states.firstOrNull()
                    ?: throw StateNotFoundOnVaultException("State with id $linearId not found.")
        }
    }
}
