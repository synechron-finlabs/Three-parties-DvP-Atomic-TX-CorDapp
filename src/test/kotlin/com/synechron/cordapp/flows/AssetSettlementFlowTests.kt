package com.synechron.cordapp.flows

import com.synechron.cordapp.state.Asset
import com.synechron.cordapp.state.AssetTransfer
import com.synechron.cordapp.state.RequestStatus
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.contracts.getCashBalances
import org.junit.Test

class AssetSettlementFlowTests : AbstractAssetJunitFlowTests() {

    @Test
    fun `process asset transfer settlement`() {
        //1. Create Asset on the ledger.
        createAsset()
        //2. Create asset transfer request.
        val stx1 = createAssetTransferRequest(lenderOfSecurity, lenderOfCashParty, cusip)
        network.waitQuiescent()
        //Get linearId of AssetTransfer request.
        val assetTransfer = lenderOfSecurity.transaction {
            val states = lenderOfSecurity.services.vaultService.queryBy(Asset::class.java).states
            assert(states.size == 1)

            val states2 = lenderOfSecurity.services.vaultService.queryBy(AssetTransfer::class.java).states
            states2.single().state.data
        }
        //3. Confirm asset transfer request.
        confirmAssetTransferRequest(lenderOfCash, custodianParty, assetTransfer.linearId)
        network.waitQuiescent()

        //Self issue cash and verify.
        selfIssueCash(lenderOfCash, DOLLARS(2000))
        lenderOfCash.transaction {
            assert(lenderOfCash.services.getCashBalances().getValue(USD) == DOLLARS(2000))
        }
        //Verify cash balance of securitySeller.
        lenderOfSecurity.transaction {
            assert(lenderOfSecurity.services.getCashBalances().isEmpty())
        }

        //4. Settle asset transfer request.
        val settleTx = settleAssetTransferRequest(globalCustodian, assetTransfer.linearId)
        network.waitQuiescent()

        lenderOfSecurity.transaction {
            assert(lenderOfSecurity.services.vaultService.queryBy(Asset::class.java).states.isEmpty())
            val assetTransfer2 = lenderOfSecurity.services.vaultService.queryBy(AssetTransfer::class.java).states.first().state.data
            assert(assetTransfer2.status == RequestStatus.TRANSFERRED)
            assert(assetTransfer2.asset.purchaseCost == lenderOfSecurity.services.getCashBalances().getValue(USD))
        }

        lenderOfCash.transaction {
            assert(lenderOfCash.services.getCashBalances().getValue(USD) == DOLLARS(1000))
            val assetTransfer3 = lenderOfCash.services.vaultService.queryBy(AssetTransfer::class.java).states.first().state.data
            assert(assetTransfer3.status == RequestStatus.TRANSFERRED)
            val assetStates = lenderOfCash.services.vaultService.queryBy(Asset::class.java).states
            assert(assetStates.size == 1)
            assert(resolveIdentity(lenderOfCash, assetStates.first().state.data.owner).name == lenderOfCashParty.name)
            assert(assetStates.first().state.data.owner == assetTransfer.securityBuyer)
            assert(assetStates.first().state.data == assetTransfer.asset.copy(owner = assetTransfer.securityBuyer))
        }

        globalCustodian.transaction {
            val assetTransfer4 = lenderOfCash.services.vaultService.queryBy(AssetTransfer::class.java).states.first().state.data
            assert(assetTransfer4.status == RequestStatus.TRANSFERRED)
        }
        //TODO Verify all Nodes able to resolve the every participants in Contract states received.
    }
}