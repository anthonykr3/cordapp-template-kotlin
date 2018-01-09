package com.invoicefinance.flows

import co.paralleluniverse.fibers.Suspendable
import com.invoicefinance.states.InvoiceState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

@InitiatedBy(VerifyInvoiceFlow::class)
class VerifyInvoiceResponseFlow(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

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