package com.invoicefinance.states

import com.invoicefinance.InvoiceSchemaV1
import net.corda.core.contracts.*
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import java.time.Instant
import java.util.*

data class InvoiceState(
        val issuance: PartyAndReference,
        val owner: AbstractParty,
        val debtor: AbstractParty,
        val invoiceAmount: Amount<Currency>,
        val dueOn: Instant,
        val verifiedForPayment: Boolean,
        override val linearId: UniqueIdentifier) : LinearState, QueryableState {
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(InvoiceSchemaV1)

    companion object {
        const val INVOICE_CONTRACT_PROGRAM_ID: ContractClassName = "com.invoicefinance.contracts.InvoiceContract"
    }

    override val participants get() = setOf(owner, debtor, issuance.party).toList()

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is InvoiceSchemaV1 -> InvoiceSchemaV1.PersistentInvoiceState(
                    issuancePartyHash = this.issuance.party.owningKey.toStringShort(),
                    issuanceRef = this.issuance.reference.bytes,
                    ownerHash = this.owner.owningKey.toStringShort(),
                    debtorHash = this.owner.owningKey.toStringShort(),
                    dueOn = this.dueOn,
                    invoiceAmount = this.invoiceAmount.quantity,
                    invoiceAmountCurrency = this.invoiceAmount.token.currencyCode,
                    verifiedForPayment = this.verifiedForPayment
            )
            else -> throw IllegalArgumentException("Unrecognised schema $schema")
        }
    }
}

