const app = angular.module('invoiceAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('DemoAppController', function($http, $location, $uibModal) {
    const demoApp = this;

    // We identify the node.
    const apiBaseURL = "/api/invoicefinance/";
    let peers = [];

    $http.get(apiBaseURL + "me").then((response) => demoApp.thisNode = response.data.me);

    $http.get(apiBaseURL + "peers").then((response) => peers = response.data.peers);

    demoApp.openModal = () => {
        const modalInstance = $uibModal.open({
            templateUrl: 'addInvoiceModal.html',
            controller: 'ModalInstanceController',
            controllerAs: 'modalInstance',
            resolve: {
                demoApp: () => demoApp,
                apiBaseURL: () => apiBaseURL,
                peers: () => peers
            }
        });

        modalInstance.result.then(() => {}, () => {});
    };

    demoApp.getInvoices = () => $http.get(apiBaseURL + "invoices")
        .then((response) => demoApp.invoices = Object.keys(response.data)
            .map((key) => response.data[key].state.data)
            .reverse());

    demoApp.getInvoices();
});

app.controller('ModalInstanceController', function ($http, $location, $uibModalInstance, $uibModal, demoApp, apiBaseURL, peers) {
    const modalInstance = this;

    modalInstance.peers = peers;
    modalInstance.form = {};
    modalInstance.formError = false;

    // Validate and create Invoice.
    modalInstance.create = () => {
        if (invalidFormInput()) {
            modalInstance.formError = true;
        } else {
            modalInstance.formError = false;

            $uibModalInstance.close();

            const createInvoiceEndpoint = `${apiBaseURL}invoices?debtorIdentity=${modalInstance.form.debtorIdentity}&invoiceAmount=${modalInstance.form.invoiceAmount}&reference=${modalInstance.form.reference}&dueOn=${modalInstance.form.dueOn}`;

            // Create PO and handle success / fail responses.
            $http.put(createInvoiceEndpoint).then(
                (result) => {
                    modalInstance.displayMessage(result);
                    demoApp.getInvoices();
                },
                (result) => {
                    modalInstance.displayMessage(result);
                }
            );
        }
    };

    modalInstance.displayMessage = (message) => {
        const modalInstanceTwo = $uibModal.open({
            templateUrl: 'messageContent.html',
            controller: 'MessageController',
            controllerAs: 'modalInstanceTwo',
            resolve: { message: () => message }
        });

        // No behaviour on close / dismiss.
        modalInstanceTwo.result.then(() => {}, () => {});
    };

    // Close create Invoice modal dialogue.
    modalInstance.cancel = () => $uibModalInstance.dismiss();

    // Validate the Invoice.
    function invalidFormInput() {
        return isNaN(modalInstance.form.invoiceAmount) || (modalInstance.form.debtorIdentity === undefined) || (modalInstance.form.reference === undefined) || (modalInstance.form.dueOn === undefined);
    }
});

// Controller for success/fail modal dialogue.
app.controller('MessageController', function ($uibModalInstance, message) {
    const modalInstanceTwo = this;
    modalInstanceTwo.message = message.data;
};