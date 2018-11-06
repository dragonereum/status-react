(ns status-im.ui.screens.wallet.events
  (:require [re-frame.core :as re-frame]
            [status-im.models.transactions :as wallet.transactions]
            [status-im.models.wallet :as models]
            [status-im.ui.screens.navigation :as navigation]
            status-im.ui.screens.wallet.navigation
            [status-im.utils.ethereum.core :as ethereum]
            [status-im.utils.ethereum.erc20 :as erc20]
            [status-im.utils.ethereum.tokens :as tokens]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.money :as money]
            [status-im.utils.prices :as prices]
            [status-im.utils.transactions :as transactions]
            [taoensso.timbre :as log]
            [status-im.utils.fx :as fx]))

(defn get-balance [{:keys [web3 account-id on-success on-error]}]
  (if (and web3 account-id)
    (.getBalance
     (.-eth web3)
     account-id
     (fn [err resp]
       (if-not err
         (on-success resp)
         (on-error err))))
    (on-error "web3 or account-id not available")))

(defn get-token-balance [{:keys [web3 contract account-id on-success on-error]}]
  (if (and web3 contract account-id)
    (erc20/balance-of
     web3
     contract
     (ethereum/normalized-address account-id)
     (fn [err resp]
       (if-not err
         (on-success resp)
         (on-error err))))
    (on-error "web3, contract or account-id not available")))

(defn assoc-error-message [db error-type err]
  (assoc-in db [:wallet :errors error-type] (or err :unknown-error)))

;; FX

(re-frame/reg-fx
 :get-balance
 (fn [{:keys [web3 account-id success-event error-event]}]
   (get-balance {:web3       web3
                 :account-id account-id
                 :on-success #(re-frame/dispatch [success-event %])
                 :on-error   #(re-frame/dispatch [error-event %])})))

(re-frame/reg-fx
 :get-tokens-balance
 (fn [{:keys [web3 symbols chain account-id success-event error-event]}]
   (doseq [symbol symbols]
     (let [contract (:address (tokens/symbol->token chain symbol))]
       (get-token-balance {:web3       web3
                           :contract   contract
                           :account-id account-id
                           :on-success #(re-frame/dispatch [success-event symbol %])
                           :on-error   #(re-frame/dispatch [error-event symbol %])})))))

;; TODO(oskarth): At some point we want to get list of relevant assets to get prices for
(re-frame/reg-fx
 :get-prices
 (fn [{:keys [from to mainnet? success-event error-event]}]
   (prices/get-prices from
                      to
                      mainnet?
                      #(re-frame/dispatch [success-event %])
                      #(re-frame/dispatch [error-event %]))))

(re-frame/reg-fx
 :update-gas-price
 (fn [{:keys [web3 success-event edit?]}]
   (ethereum/gas-price web3 #(re-frame/dispatch [success-event %2 edit?]))))

(re-frame/reg-fx
 :update-estimated-gas
 (fn [{:keys [web3 obj success-event]}]
   (ethereum/estimate-gas-web3 web3 (clj->js obj) #(re-frame/dispatch [success-event %2]))))

;; Handlers
(handlers/register-handler-fx
 :update-wallet
 (fn [cofx _]
   (models/update-wallet cofx)))

(handlers/register-handler
 :update-transactions
 (fn [cofx _]
   {::wallet.transactions/sync-transactions-now nil}))

(handlers/register-handler-fx
 :update-balance-success
 (fn [{:keys [db]} [_ balance]]
   {:db (-> db
            (assoc-in [:wallet :balance :ETH] balance)
            (assoc-in [:wallet :balance-loading?] false))}))

(handlers/register-handler-fx
 :update-balance-fail
 (fn [{:keys [db]} [_ err]]
   (log/debug "Unable to get balance: " err)
   {:db (-> db
            (assoc-error-message :balance-update :error-unable-to-get-balance)
            (assoc-in [:wallet :balance-loading?] false))}))

(fx/defn update-token-balance-success [{:keys [db]} symbol balance]
  {:db (-> db
           (assoc-in [:wallet :balance symbol] balance)
           (assoc-in [:wallet :balance-loading?] false))})

(handlers/register-handler-fx
 :update-token-balance-success
 (fn [cofx [_ symbol balance]]
   (update-token-balance-success cofx symbol balance)))

(handlers/register-handler-fx
 :update-token-balance-fail
 (fn [{:keys [db]} [_ symbol err]]
   (log/debug "Unable to get token " symbol "balance: " err)
   {:db (-> db
            (assoc-error-message :balance-update :error-unable-to-get-token-balance)
            (assoc-in [:wallet :balance-loading?] false))}))

(handlers/register-handler-fx
 :update-prices-success
 (fn [{:keys [db]} [_ prices]]
   {:db (assoc db
               :prices prices
               :prices-loading? false)}))

(handlers/register-handler-fx
 :update-prices-fail
 (fn [{:keys [db]} [_ err]]
   (log/debug "Unable to get prices: " err)
   {:db (-> db
            (assoc-error-message :prices-update :error-unable-to-get-prices)
            (assoc :prices-loading? false))}))

(handlers/register-handler-fx
 :show-transaction-details
 (fn [{:keys [db]} [_ hash]]
   {:db       (assoc-in db [:wallet :current-transaction] hash)
    :dispatch [:navigate-to :wallet-transaction-details]}))

(handlers/register-handler-fx
 :wallet/show-sign-transaction
 (fn [{:keys [db]} [_ {:keys [id method]} from-chat?]]
   {:db       (assoc-in db [:wallet :send-transaction] {:id         id
                                                        :method     method
                                                        :from-chat? from-chat?})
    :dispatch [:navigate-to-clean :wallet-send-transaction-modal]}))

(handlers/register-handler-fx
 :wallet/update-gas-price-success
 (fn [{:keys [db] :as cofx} [_ price edit?]]
   (if edit?
     (models/edit-value
      :gas-price
      (money/to-fixed
       (money/wei-> :gwei price))
      cofx)
     {:db (assoc-in db [:wallet :send-transaction :gas-price] price)})))

(handlers/register-handler-fx
 :wallet/update-estimated-gas
 (fn [{:keys [db]} [_ obj]]
   {:update-estimated-gas {:web3          (:web3 db)
                           :obj           obj
                           :success-event :wallet/update-estimated-gas-success}}))

(handlers/register-handler-fx
 :wallet/update-estimated-gas-success
 (fn [{:keys [db]} [_ gas]]
   (when gas
     {:db (assoc-in db [:wallet :send-transaction :gas] (money/bignumber (int (* gas 1.2))))})))

(handlers/register-handler-fx
 :wallet-setup-navigate-back
 (fn [{:keys [db] :as cofx}]
   (fx/merge cofx
             {:db (assoc-in db [:wallet :send-transaction] {})}
             (navigation/navigate-back))))
