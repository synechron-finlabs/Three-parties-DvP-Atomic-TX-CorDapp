package com.synechron.cordapp.flows

import com.synechron.cordapp.state.AssetTransfer
import net.corda.testing.internal.chooseIdentity
import org.junit.Test
import kotlin.test.assertEquals

class CreateAssetTransferRequestFlowTests : AbstractAssetJunitFlowTests() {

    @Test
    fun `create asset transfer request successfully`() {
        val lenderOfSecurityParty = lenderOfSecurity.info.chooseIdentity()
        val lenderOfCashParty = lenderOfCash.info.chooseIdentity()

        //1. Create Asset on the ledger.
        createAsset()
        //2. Create asset transfer request.
        val stx = createAssetTransferRequest(lenderOfSecurity, lenderOfCashParty, cusip)
        network.waitQuiescent()

        val assetTransfer1 = lenderOfCash.services.loadState(stx.tx.outRef<AssetTransfer>(0).ref).data as AssetTransfer
        val assetTransfer2 = lenderOfSecurity.services.loadState(stx.tx.outRef<AssetTransfer>(0).ref).data as AssetTransfer

        assertEquals(assetTransfer1, assetTransfer2)

        val maybePartyALookedUpByA = resolveIdentity(lenderOfSecurity, assetTransfer1.securitySeller)
        val maybePartyALookedUpByB = resolveIdentity(lenderOfSecurity, assetTransfer1.securityBuyer)

        assertEquals(lenderOfSecurityParty, maybePartyALookedUpByA)
        assertEquals(lenderOfCashParty, maybePartyALookedUpByB)

        val maybePartyBLookedUpByA = resolveIdentity(lenderOfCash, assetTransfer1.securityBuyer)
        val maybePartyBLookedUpByB = resolveIdentity(lenderOfCash, assetTransfer1.securitySeller)

        assertEquals(lenderOfCashParty, maybePartyBLookedUpByA)
        assertEquals(lenderOfSecurityParty, maybePartyBLookedUpByB)
    }
}