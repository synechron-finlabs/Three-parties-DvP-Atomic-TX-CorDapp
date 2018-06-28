package com.synechron.cordapp.flows

import com.synechron.cordapp.state.Asset
import net.corda.finance.DOLLARS
import org.junit.Test
import kotlin.test.assertEquals

class CreateAssetStateFlowTests : AbstractAssetJunitFlowTests() {

    @Test
    fun `create Asset on ledger successfully`() {
        val stx = createAsset(lenderOfSecurity, cusip, "US BOND", DOLLARS(1000))
        network.waitQuiescent()

        val asset = lenderOfSecurity.services.loadState(stx.tx.outRef<Asset>(0).ref).data as Asset

        assertEquals(asset.cusip, cusip)
    }
}
