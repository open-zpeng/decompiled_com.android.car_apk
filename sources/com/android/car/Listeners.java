package com.android.car;

import android.os.IBinder;
import com.android.car.Listeners.IListener;
import java.util.Iterator;
import java.util.LinkedList;
/* loaded from: classes3.dex */
public class Listeners<ClientType extends IListener> {
    private final LinkedList<ClientWithRate<ClientType>> mClients = new LinkedList<>();
    private int mRate;

    /* loaded from: classes3.dex */
    public interface IListener extends IBinder.DeathRecipient {
        void release();
    }

    /* loaded from: classes3.dex */
    public static class ClientWithRate<ClientType extends IListener> {
        private final ClientType mClient;
        private int mRate;

        /* JADX INFO: Access modifiers changed from: package-private */
        public ClientWithRate(ClientType client, int rate) {
            this.mClient = client;
            this.mRate = rate;
        }

        public boolean equals(Object o) {
            if ((o instanceof ClientWithRate) && this.mClient == ((ClientWithRate) o).mClient) {
                return true;
            }
            return false;
        }

        public int hashCode() {
            return this.mClient.hashCode();
        }

        int getRate() {
            return this.mRate;
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public void setRate(int rate) {
            this.mRate = rate;
        }

        /* JADX INFO: Access modifiers changed from: package-private */
        public ClientType getClient() {
            return this.mClient;
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public Listeners(int rate) {
        this.mRate = rate;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getRate() {
        return this.mRate;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void setRate(int rate) {
        this.mRate = rate;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public boolean updateRate() {
        int fastestRate = 1;
        Iterator<ClientWithRate<ClientType>> it = this.mClients.iterator();
        while (it.hasNext()) {
            ClientWithRate<ClientType> clientWithRate = it.next();
            int clientRate = clientWithRate.getRate();
            if (clientRate < fastestRate) {
                fastestRate = clientRate;
            }
        }
        if (this.mRate != fastestRate) {
            this.mRate = fastestRate;
            return true;
        }
        return false;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void addClientWithRate(ClientWithRate<ClientType> clientWithRate) {
        this.mClients.add(clientWithRate);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void removeClientWithRate(ClientWithRate<ClientType> clientWithRate) {
        this.mClients.remove(clientWithRate);
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public int getNumberOfClients() {
        return this.mClients.size();
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public Iterable<ClientWithRate<ClientType>> getClients() {
        return this.mClients;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public ClientWithRate<ClientType> findClientWithRate(ClientType client) {
        Iterator<ClientWithRate<ClientType>> it = this.mClients.iterator();
        while (it.hasNext()) {
            ClientWithRate<ClientType> clientWithRate = it.next();
            if (clientWithRate.getClient() == client) {
                return clientWithRate;
            }
        }
        return null;
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public void release() {
        Iterator<ClientWithRate<ClientType>> it = this.mClients.iterator();
        while (it.hasNext()) {
            ClientWithRate<ClientType> clientWithRate = it.next();
            clientWithRate.getClient().release();
        }
        this.mClients.clear();
    }
}
