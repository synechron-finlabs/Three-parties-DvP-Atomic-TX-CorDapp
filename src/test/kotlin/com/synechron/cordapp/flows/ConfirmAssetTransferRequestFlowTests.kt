package com.synechron.cordapp.flows

import com.synechron.cordapp.state.AssetTransfer
import com.synechron.cordapp.state.RequestStatus
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test

class ConfirmAssetTransferRequestFlowTests : AbstractAssetJunitFlowTests() {

    @Test
    fun `confirm asset transfer request successfully`() {
        //1. Create Asset on the ledger.
        createAsset()
        //2. Create asset transfer request.
        val stx1 = createAssetTransferRequest(lenderOfSecurity, lenderOfCashParty, cusip)
        network.waitQuiescent()
        var linearId: UniqueIdentifier? = null
        lenderOfCash.transaction {
            linearId = (lenderOfCash.services.loadState(stx1.tx.outRef<AssetTransfer>(0).ref).data as AssetTransfer).linearId
        }
        //3. Confirm asset transfer request.
        val stx2 = confirmAssetTransferRequest(lenderOfCash, custodianParty, linearId!!)
        network.waitQuiescent()

        var assetTransfer : AssetTransfer? = null
        globalCustodian.transaction {
         assetTransfer = globalCustodian.services.loadState(stx2.tx.outRef<AssetTransfer>(0).ref).data as AssetTransfer
        assert(assetTransfer!!.status == RequestStatus.PENDING)
        }
        val maybePartyBLookedUpByC = resolveIdentity(globalCustodian, assetTransfer!!.securityBuyer)
        val maybePartyALookedUpByC = resolveIdentity(globalCustodian, assetTransfer!!.securitySeller)
        assert(lenderOfSecurityParty == maybePartyALookedUpByC)
        assert(lenderOfCashParty == maybePartyBLookedUpByC)
    }
}