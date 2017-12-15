package com.invoicefinance

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

class InvoiceContract : Contract {
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<InvoiceContract.Commands>()
        val timeWindow = tx.timeWindow

        when (command.value) {
            is Commands.Create -> {
                requireThat {
                    "No inputs must be consumed when issuing an invoice" using (tx.inputStates.isEmpty())

                    "There must only be one input" using (tx.outputStates.size == 1)
                    var invoices = tx.outputsOfType<InvoiceState>()
                    "The input must be of type InvoiceState" using (invoices.size == 1)

                    var invoice = invoices.single()
                    "The invoice amount should be greater than zero" using (invoice.invoiceAmount.quantity > 0)
                    "The issuer and the debtor cannot be the same entity" using (invoice.debtor != invoice.issuance.party)
                    "The owner and the debtor cannot be the same entity" using (invoice.debtor != invoice.owner)
                    "The owner is the issuer on issuance" using (invoice.owner == invoice.issuance.party)
                    "The invoice creator can't verify it" using (invoice.verifiedForPayment == false)

                    "There must be two signatories" using (command.signers.toSet().size == 2)
                    "The seller must sign the command" using (command.signers.containsAll(listOf(invoice.debtor.owningKey, invoice.issuance.party.owningKey, invoice.owner.owningKey)))

                    timeWindow?.untilTime ?: throw IllegalArgumentException("Issuances must be timestamped")
                }
            }
            is Commands.Move -> {
                requireThat {

                }
            }

            is Commands.Verify -> {

            }

            else -> throw IllegalArgumentException("Unrecognised Command")
        }
    }

    interface Commands : CommandData {
        class Create() : TypeOnlyCommandData(), Commands
        class Move() : TypeOnlyCommandData(), Commands
        class Verify() : TypeOnlyCommandData(), Commands
    }
}

