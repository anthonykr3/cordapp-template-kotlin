package com.invoicefinance.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction

@InitiatedBy(GenerateInvoiceFlow::class)
class GenerateInvoiceResponseFlow(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object: SignTransactionFlow(otherPartySession, tracker()) {
            override fun checkTransaction(stx: SignedTransaction) {
                //I'll agree to anything!
            }
        }

        subFlow(signTransactionFlow)
    }
}