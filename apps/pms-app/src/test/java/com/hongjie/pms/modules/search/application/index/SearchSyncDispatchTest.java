package com.hongjie.pms.modules.search.application.index;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import org.springframework.transaction.TransactionDefinition;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SearchSyncDispatchTest {
    SearchIndexCoordinator coordinator=mock(SearchIndexCoordinator.class);
    TransactionTemplate transaction=new TransactionTemplate(new AbstractPlatformTransactionManager(){
        protected Object doGetTransaction(){return new Object();}
        protected void doBegin(Object transaction,TransactionDefinition definition){}
        protected void doCommit(DefaultTransactionStatus status){}
        protected void doRollback(DefaultTransactionStatus status){}
    });
    @Test void enqueueOnlyAfterCommit(){
        SearchSyncDispatch dispatch=new SearchSyncDispatch(coordinator,Runnable::run);
        transaction.executeWithoutResult(status->{dispatch.submit("pet",1L);verifyNoInteractions(coordinator);});
        verify(coordinator).sync("pet",1L);
    }
    @Test void rolledBackPublicationNeverTouchesIndex(){
        SearchSyncDispatch dispatch=new SearchSyncDispatch(coordinator,Runnable::run);
        transaction.executeWithoutResult(status->{dispatch.submit("pet",1L);status.setRollbackOnly();});
        verifyNoInteractions(coordinator);
    }
    @Test void outsideTransactionWorksAndFailuresAreObservable(){
        SearchSyncDispatch dispatch=new SearchSyncDispatch(coordinator,Runnable::run);
        doThrow(new IllegalStateException("injected")).when(coordinator).sync("pet",1L);
        dispatch.submit("pet",1L);assertThat(dispatch.failures()).isEqualTo(1);
    }
    @Test void rejectedQueueDoesNotBreakCommittedBusinessTransaction(){
        SearchSyncDispatch dispatch=new SearchSyncDispatch(coordinator,r->{throw new RejectedExecutionException("full");});
        assertThatCode(()->transaction.executeWithoutResult(status->dispatch.submit("pet",1L))).doesNotThrowAnyException();
        assertThat(dispatch.failures()).isEqualTo(1);verifyNoInteractions(coordinator);
    }
}
