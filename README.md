# Three-way flows - DvP atomic transaction - CorDapp

This CorDapp creates Delivery-vs-Payment atomic transaction for on ledger `Asset` transfer for `Cash` with three participants.

**CorDapp Nodes:**

* Security Seller:  This party is owner of `Asset` state of type `OwnableState` on ledger. He sells these assets for cash by creating Corda transaction.
* Security Buyer: This party has some `Cash` tokens on his ledger. He purchases `Asset` securities for Cash.
* Clearing House: coordinates `Seller` and `Buyer` parties to process the settlement transaction. It initiate settlement of `Asset` transfer request and collect the required states (i.e. Asset and Cash) from counterparties (i.e. Seller and Buyer) in-order to complete the transaction.
* Notary: notary node to check double-spend of input states then verify and sign final transaction.

**This CorDapp example business-logic flows as below:**

* 1. The `Seller` party creates the `Asset` state of type `OwnableState` on ledger.
* 2. Create `AssetTransfer` state and share it with `Buyer` party who willing to buy the `Asset`. 
* 3. The `Buyer` party review and accept the `AssetTransfer` transaction and further share this state with `ClearingHouse` party.
* 4. The `ClearingHouse` party - offline review and validate the `Asset` data received along with `AssetTransfer` state. If everything is good, he initiate the `AssetSettlementInitiatorFlow` flow to create the settlement transaction with three participants. On completion of settlement transaction `Buyer` party became owner of `Asset` state and issues `Cash` tokens equals to the amount of `Asset.purchaseCost` to `Seller` party on ledger.

## Interacting with the CorDapp via Corda Shell

Use relevant node's Shell console to initiate the flows.

**Let's start flows from each Corda node's shell console step-by-step:**

**1.** To create `Asset` - run below command on `SecuritySeller` node's console:
```console
flow start com.synechron.cordapp.seller.flows.CreateAssetStateFlow$Initiator cusip: "CUSIP222", assetName: "US Bond", purchaseCost: $10000
```
To see state created run below command:
```console
run vaultQuery contractStateType: com.synechron.cordapp.state.Asset
```
Now we have on ledger asset of type OwnableState and ready to sell it to other party on network.
    
**2.** To create `AssetTransfer` - run below command again on `SecuritySeller` node's console:
```console
flow start com.synechron.cordapp.seller.flows.CreateAssetTransferRequestInitiatorFlow cusip: "CUSIP222", securityBuyer: "O=SecurityBuyer,L=New York,C=US"
```
To see `AssetTransfer` state created run below command again:
```console    
run vaultQuery contractStateType: com.synechron.cordapp.state.AssetTransfer
```    
You can see the AssetTransfer state data and **copy** the `linearId.id` *field value* of it and save it with you as we required it in next step.

**3.** The `Buyer` party confirm `AssetTransfer` request received by running below command on counterparty `SecurityBuyer` node's console:
```console
flow start com.synechron.cordapp.buyer.flows.ConfirmAssetTransferRequestInitiatorFlow linearId: "<Replace with AssetTransfer.linearId.id>", clearingHouse: "O=ClearingHouse,L=New York,C=US"
```
This flow update the `AssetTransfer.status` value to `PENDING` from it's initial value `PENDING_CONFIRMATION`.

**4.** The `Buyer` party self-issue the `Cash` tokens on ledger using:
```console
flow start net.corda.finance.flows.CashIssueFlow amount: $20000, issuerBankPartyRef: 1234, notary: "O=Notary,L=New York,C=US"
```
To see the issued cash balance run:
```console
run vaultQuery contractStateType: net.corda.finance.contracts.asset.Cash$State
```    
**5.** Run below command on the `ClearingHouse` party node's console to create settlement transaction and distribute to three participants:
```console
flow start com.synechron.cordapp.clearinghouse.flows.AssetSettlementInitiatorFlow linearId: "<Replace with AssetTransfer.linearId.id>"
```
On successful completion of flows the `AssetTransfer.status` field value became `TRANSFERRED`. Now you can cross-check the available `Asset` and `Cash` states on each counterparty corda node.
