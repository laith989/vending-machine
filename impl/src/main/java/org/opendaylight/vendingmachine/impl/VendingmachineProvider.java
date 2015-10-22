/*
 * Copyright(c) Inocybe. Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vendingmachine.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.DisplayString;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.MakeOrderInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.RefullItemInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.Vendingmachine;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.Vendingmachine.VendingmachineStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.VendingmachineBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.VendingmachineOutOfItemsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.VendingmachineService;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;



public class VendingmachineProvider implements BindingAwareProvider, VendingmachineService,DataChangeListener, AutoCloseable  {
	
	public static final InstanceIdentifier<Vendingmachine> VENDINGMACHINE_IID = InstanceIdentifier.builder(Vendingmachine.class).build();
	
	private static final Logger LOG = LoggerFactory.getLogger(VendingmachineProvider.class);
    private static final DisplayString vendingmachine_MANUFACTURE = new DisplayString ("OpenDayLight");
    private static final DisplayString vendingmachine_MODEL_NUMBER = new DisplayString ("Model 1 - Binding Aware");
    
    private ProviderContext providerContext;
    private DataBroker dataProvider ;
    private ListenerRegistration<DataChangeListener> dcReg;
    private BindingAwareBroker.RpcRegistration<VendingmachineService> rpcReg;
    
    private NotificationProviderService notificationService;
    private final ExecutorService executor;
    
    private final AtomicReference<Future<?>> currentMakeOrderTask = new AtomicReference<>();
    private final AtomicLong amountOfProductInStock = new AtomicLong(10);
    private final AtomicLong ordersMade = new AtomicLong(0);
    private final AtomicLong maxOrderItems = new AtomicLong(3);
    
    public VendingmachineProvider(){
    	executor = Executors.newFixedThreadPool(1);
    }
   
	@Override
    public void close() throws Exception {
		if (dataProvider != null){
			executor.shutdown();
		
		WriteTransaction tx = dataProvider.newWriteOnlyTransaction();
		Futures.addCallback(tx.submit(), new FutureCallback<Void>(){
			
			@Override
    		public void onSuccess (final Void result){
    			LOG.debug("Delete VM commit result: {}", result);
    			
    		}
    		
    		@Override
    		public void onFailure (final Throwable t){
    			LOG.error("Delete VM failed", t);
     		}
		});
    	
        dcReg.close();
        rpcReg.close();
        LOG.info("VendingmachineProvider Closed");
        }
    }
       
    @Override
    public void onSessionInitiated(ProviderContext session) {
    	
    	//LOG.info("Hello World!");
    	this.providerContext = session;
    	this.dataProvider = session.getSALService(DataBroker.class);
    	this.notificationService = session.getSALService(NotificationProviderService.class);
      	if (dataProvider != null){
    	dcReg = dataProvider.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, VENDINGMACHINE_IID , this , DataChangeScope.SUBTREE);
    	
    	rpcReg = session.addRpcImplementation(VendingmachineService.class, this);
      	
    	initVendingmachineOperational();
    	initVendingmachineConfiguration();
    	}
    	LOG.info("onSessionIntitiated: initialization done");
      	    	
    }
    
    
    @Override
    public Future<RpcResult<Void>> makeOrder(final MakeOrderInput input){
    
    	LOG.info("makeOrder: {}", input);
    	
    	final SettableFuture<RpcResult<Void>> futureResult = SettableFuture.create();
    	
    	checkStatusAndMakeOrderItem(input, futureResult, 2);
    	LOG.info("makeOreder returning...");
    	return futureResult;
    }
    
    
    
    @Override
    public Future<RpcResult<java.lang.Void>> refullItem(final RefullItemInput input){
    
    	LOG.info("processOrder: {}", input);
    	
    	amountOfProductInStock.set(input.getQuantityofproductprovide());
    	return Futures.immediateFuture( RpcResultBuilder.<Void> success().build());
    }
    
    
    public void onDataChanged( final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change ) {
        DataObject dataObject = change.getUpdatedSubtree();
        if( dataObject instanceof Vendingmachine ) {
        	Vendingmachine vendingmachine = (Vendingmachine) dataObject;
        	
            LOG.info("onDataChanged - new Vendingmachine config: {}", vendingmachine);
            
            
        } 
    }
    
    private void initVendingmachineOperational (){
    	Vendingmachine vendingmachine = buildVendingmachine(VendingmachineStatus.Availability);
    			
    	
    	WriteTransaction tx = dataProvider.newWriteOnlyTransaction();
    	tx.put(LogicalDatastoreType.OPERATIONAL, VENDINGMACHINE_IID, vendingmachine);
    	
    	Futures.addCallback(tx.submit(), new FutureCallback<Void>(){
    		@Override
    		public void onSuccess (final Void result){
    			LOG.info("initVendingmachineOperational: Transaction succeeded");
    		}
    		@Override
    		public void onFailure (final Throwable t){
    			LOG.info("initVendingmachineOperational: Transaction failed");
    		}
    		
    	});
    	LOG.error("initVendingmachineOperational: operational status populated: {}", vendingmachine);
    }
    
    private void initVendingmachineConfiguration(){
    	Vendingmachine vendingmachine = new VendingmachineBuilder().setNumberOfProductsAvailable((long)8).build();
    	
    	WriteTransaction tx = dataProvider.newWriteOnlyTransaction();
    	tx.put(LogicalDatastoreType.CONFIGURATION, VENDINGMACHINE_IID, vendingmachine);
    	
    	Futures.addCallback(tx.submit(), new FutureCallback<Void>(){
    		
    		
    		public void onSuccess (final Void result){
    			LOG.info("initVendingmachineOperational: Transaction succeeded");
    		}
    		@Override
    		public void onFailure (final Throwable t){
    			LOG.info("initVendingmachineOperational: Transaction failed");
    		}
    		
    	});
    }
    
    private Vendingmachine buildVendingmachine (final VendingmachineStatus status){
    	return new VendingmachineBuilder()
    			.setVendingmachineManufacturer( vendingmachine_MANUFACTURE )
    			.setVendingmachineModelNumber( vendingmachine_MODEL_NUMBER )
    			.setVendingmachineStatus( status )
    			.build();
    }
    private RpcError MakeOrderOutOfStockError (){
    	return RpcResultBuilder.newError(ErrorType.APPLICATION,"Prosses denied", "the Vending Machine in Out of item", "out-of-stock", null, null);
    }
    
    private RpcError MakeOrderInUseError(){
    	return RpcResultBuilder.newError(ErrorType.APPLICATION, "In-Progress", "Vending Machine is busy", null, null, null);
    	
    }
    
    private void setVendingMachineStatusAvaliable (final Function<Boolean, Void> resultCallback){
    	
    	WriteTransaction tx = dataProvider.newWriteOnlyTransaction();
    	tx.put(LogicalDatastoreType.OPERATIONAL,  VENDINGMACHINE_IID, buildVendingmachine (VendingmachineStatus.Availability));
    	
    	Futures.addCallback(tx.submit(), new FutureCallback<Void>(){
    	
    		@Override
    		public void onSuccess (final Void result){
    			notifyCallback(true);
    		}
    		
    		@Override
    		public void onFailure (final Throwable t){
    			LOG.error("Failed to update Vendingmachine Stutus", t);
    			notifyCallback (false);
    		}
    		void notifyCallback (final boolean result){
    			if (resultCallback != null){
    				resultCallback.apply(result);
    			}
    		}
    	});
    	
    }
        private boolean outOfStock(){
    	return amountOfProductInStock.get() == 0;
    }
    private void checkStatusAndMakeOrderItem (final MakeOrderInput input, final SettableFuture<RpcResult<Void>> futureResult, final int tries){
    	LOG.info("checkStatusAndMakeOrderItem");
    	
    	final ReadWriteTransaction tx = dataProvider.newReadWriteTransaction();
    	ListenableFuture<Optional<Vendingmachine>> readFuture = tx.read(LogicalDatastoreType.OPERATIONAL, VENDINGMACHINE_IID);
    	
    	
    	final ListenableFuture<Void> commitFuture = Futures.transform(readFuture,  new AsyncFunction<Optional<Vendingmachine>,Void>(){
    		
    		@Override
    		public ListenableFuture<Void> apply(
    				final Optional<Vendingmachine> vendingmachineData) throws Exception{
    			
    			VendingmachineStatus vendingmachineStatus = VendingmachineStatus.Availability;
    			if (vendingmachineData.isPresent()){
    				vendingmachineStatus = vendingmachineData.get().getVendingmachineStatus();
    			}
    			
    			LOG.debug("Read Vending Machine status: {}", vendingmachineStatus);
    			if (vendingmachineStatus == VendingmachineStatus.Availability){
    				if (outOfStock()){
    					LOG.debug("Vending Machine is out or Products");
    					return Futures.immediateFailedCheckedFuture(new TransactionCommitFailedException("", MakeOrderOutOfStockError()));
    				}
    				
    				LOG.debug("Setting Vendingmachine status to empty");
    				
    				tx.put(LogicalDatastoreType.OPERATIONAL, VENDINGMACHINE_IID, buildVendingmachine (VendingmachineStatus.Empty));
    				return tx.submit();
    			}
    			
    			LOG.debug("Your Order In Progress");
    			
    			return Futures.immediateFailedCheckedFuture(new TransactionCommitFailedException("", MakeOrderInUseError()));
    		}
    		
    	});
    	
    	Futures.addCallback( commitFuture, new FutureCallback<Void>(){
    		
    		@Override
    		public void onSuccess (final Void result){
    			currentMakeOrderTask.set(executor.submit(new makeOrderTask(input, futureResult)));
    		}
    		
    		@Override
    		public void onFailure (final Throwable ex){
    			if (ex instanceof OptimisticLockFailedException){
    				
    				if ((tries -1) > 0){
    					LOG.debug("Got OptimisticLockFailedExceptionp trying agin ");
    					
    					checkStatusAndMakeOrderItem (input, futureResult, tries -1);
    				}
    				else {
    					futureResult.set(RpcResultBuilder.<Void>failed().withError(ErrorType.APPLICATION, ex.getMessage()).build());
    				}
    			}
    			else {
    				LOG.debug("Failed to commit VM status", ex);
    				
    				futureResult.set(RpcResultBuilder.<Void>failed().withRpcErrors(((TransactionCommitFailedException)ex).getErrorList()).build());
    			}
    		}
    	});
    }
    
    private class makeOrderTask implements Callable<Void>{
    	
    	final MakeOrderInput itemRequest;
    	final SettableFuture<RpcResult<Void>> futureResult;
    	
    	public makeOrderTask (final MakeOrderInput itemRequest, final SettableFuture<RpcResult<Void>> futureResult){
    		
    		this.itemRequest = itemRequest;
    		this.futureResult = futureResult;
    	}
    	
    	@Override 
    	public Void call(){
    		
    		try {
    			long maxOrderItems = VendingmachineProvider.this.maxOrderItems.get();
    			Thread.sleep(5);
    		}
    		catch (InterruptedException e){
    			LOG.info("Interrupted while making the order");
    		}
    		
    		ordersMade.incrementAndGet();
    		amountOfProductInStock.getAndDecrement();
    		
    		if(outOfStock() ){
    			LOG.info("Vending Machine is out of item, SORRY");
    			
    			notificationService.publish( new VendingmachineOutOfItemsBuilder().build());
    		}
    		
    		setVendingMachineStatusAvaliable (new Function<Boolean, Void>(){
    			
    			@Override
    			public Void apply (final Boolean result){
    				currentMakeOrderTask.set(null);
    				LOG.debug("Progress done");
    				
    				futureResult.set(RpcResultBuilder.<Void>success().build());
    				return null;
    				
    			}
    		});
    		
    		return null;
    	}
    	
    }
    
}
