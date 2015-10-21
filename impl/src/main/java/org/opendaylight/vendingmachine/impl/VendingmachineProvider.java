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
//import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
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

public class VendingmachineProvider implements BindingAwareProvider, AutoCloseable {
	
	public static final InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.Vendingmachine> VENDINGMACHINE_IID = InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vendingmachine.rev141210.Vendingmachine.class).build();
    private static final Logger LOG = LoggerFactory.getLogger(VendingmachineProvider.class);
    private static final DisplayString vendingmachine_MANUFACTURE = new DisplayString ("OpenDayLight");
    private static final DisplayString vendingmachine_MODEL_NUMBER = new DisplayString ("Model 1 - Binding Aware");
    
    private ProviderContext providerContext;
    private DataBroker dataProvider ;
   // private ListenerRegistration<DataChangeListener> dcReg;
        
   
     
    private Vendingmachine buildVendingmachine( VendingmachineStatus status ) {

        return new VendingmachineBuilder().setVendingmachineManufacturer( vendingmachine_MANUFACTURE )
                                   .setVendingmachineModelNumber( vendingmachine_MODEL_NUMBER )
                                   .setVendingmachineStatus( status )
                                   .build();
    }
   
    @Override
    public void onSessionInitiated(ProviderContext session) {
        //LOG.info("VendingmachineProvider Session Initiated");
    	LOG.info("Hello World!");
    	this.providerContext = session;
    	this.dataProvider = session.getSALService(DataBroker.class);
    	
    	//dcReg = dataProvider.registerDataChangeListener( LogicalDatastoreType.CONFIGURATION, VENDINGMACHINE_IID,this, DataChangeScope,SUBTREE);
   
    	
    }
    
  //  @Override
 /*   public void onDataChanged( final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change ) {
        DataObject dataObject = change.getUpdatedSubtree();
        if( dataObject instanceof Vendingmachine ) {
        	Vendingmachine vendingmachine = (Vendingmachine) dataObject;
            LOG.info("onDataChanged - new Vendingmachine config: {}", vendingmachine);
            
            
        } else {
            LOG.warn("onDataChanged - not instance of Vendingmachine {}", dataObject);
          }
      }

 */

	@Override
    public void close() throws Exception {
    //	dcReg.close();
        LOG.info("VendingmachineProvider Closed");
    }

}
