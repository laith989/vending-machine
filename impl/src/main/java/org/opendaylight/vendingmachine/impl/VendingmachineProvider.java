/*
 * Copyright(c) Inocybe. Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vendingmachine.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.DisplayString;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.Vendingmachine;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.Vendingmachine.VendingmachineStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.VendingmachineBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class VendingmachineProvider implements BindingAwareProvider, AutoCloseable, DataChangeListener {
	
	public static final InstanceIdentifier<Vendingmachine> VENDINGMACHINE_IID = InstanceIdentifier.builder(Vendingmachine.class).build();
	private static final Logger LOG = LoggerFactory.getLogger(VendingmachineProvider.class);
    private static final DisplayString vendingmachine_MANUFACTURE = new DisplayString ("OpenDayLight");
    private static final DisplayString vendingmachine_MODEL_NUMBER = new DisplayString ("Model 1 - Binding Aware");
    
    private ProviderContext providerContext;
    private DataBroker dataProvider ;
    private ListenerRegistration<DataChangeListener> dcReg;
        
	@Override
    public void close() throws Exception {
    	
        LOG.info("VendingmachineProvider Closed");
        dcReg.close();
    }
       
    @Override
    public void onSessionInitiated(ProviderContext session) {
    	
    	//LOG.info("Hello World!");
    	this.providerContext = session;
    	this.dataProvider = session.getSALService(DataBroker.class);
      	
    	dcReg = dataProvider.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, VENDINGMACHINE_IID , this , DataChangeScope.SUBTREE);
    	
    	initVendingmachineOperational();
    	LOG.info("onSessionIntitiated: initialization done");
    	
    	
    }
    
    public void onDataChanged( final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change ) {
        DataObject dataObject = change.getUpdatedSubtree();
        if( dataObject instanceof Vendingmachine ) {
        	Vendingmachine vendingmachine = (Vendingmachine) dataObject;
            LOG.info("onDataChanged - new Vendingmachine config: {}", vendingmachine);
            
            
        } else {
            LOG.warn("onDataChanged - not instance of Vendingmachine {}", dataObject);
          }
      }
    
    private void initVendingmachineOperational (){
    	Vendingmachine vendingmachine = new VendingmachineBuilder().setVendingmachineManufacturer( vendingmachine_MANUFACTURE )
        .setVendingmachineModelNumber( vendingmachine_MODEL_NUMBER )
        .setVendingmachineStatus( VendingmachineStatus.Availability )
        .build();
    	
    	WriteTransaction tx = dataProvider.newWriteOnlyTransaction();
    	tx.put(LogicalDatastoreType.OPERATIONAL, VENDINGMACHINE_IID, vendingmachine);
    	
    	/*Futures.addCallback(tx.submit(), new FutureCallback<Void>(){
    		@Override
    		public void onSuccess (final Void result){
    			LOG.info("initVendingmachineOperational: Transaction succeeded");
    		}
    		@Override
    		public void onFailure (final Throwable t){
    			LOG.info("initVendingmachineOperational: Transaction failed");
    		}
    		
    	});*/
    	LOG.error("initVendingmachineOperational: operational status populated: {}", vendingmachine);
    }
    
  //  @Override


 



}
