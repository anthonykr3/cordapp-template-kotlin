package com.invoicefinance.flows

import co.paralleluniverse.fibers.Suspendable
import com.invoicefinance.InvoiceState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

@InitiatedBy(GenerateInvoiceFlow::class)
class GenerateInvoiceResponseFlow(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    companion object {
        object STARTING_STEP : ProgressTracker.Step("Starting")

        object SIGNATURE_STEP : ProgressTracker.Step("Step 2")

        fun tracker() = ProgressTracker(
                STARTING_STEP,
                SIGNATURE_STEP
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = STARTING_STEP
        val signTransactionFlow = object: SignTransactionFlow(otherPartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {

                val output = stx.tx.outputs.single().data
                "This is an Invoice" using (output is InvoiceState)
            }
        }

        return subFlow(signTransactionFlow)
    }
}

@InitiatedBy(VerifyInvoiceFlow::class)
class VerifyInvoiceResponseFlow(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object: SignTransactionFlow(otherPartySession, tracker()) {
            override fun checkTransaction(stx: SignedTransaction) {
                //I'll agree to anything... still!
            }
        }
    }
}