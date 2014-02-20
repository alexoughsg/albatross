// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.cloud.event;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import com.cloud.domain.Domain;
import com.cloud.domain.dao.DomainDao;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.framework.events.EventBus;
import org.apache.cloudstack.framework.events.EventBusException;

import com.cloud.event.dao.EventDao;
import com.cloud.server.ManagementServer;
import com.cloud.user.Account;
import com.cloud.user.AccountVO;
import com.cloud.user.User;
import com.cloud.user.dao.AccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.component.ComponentContext;

public class ActionEventUtils {
    private static final Logger s_logger = Logger.getLogger(ActionEventUtils.class);

    private static EventDao _eventDao;
    private static DomainDao _domainDao;
    private static AccountDao _accountDao;
    protected static UserDao _userDao;
    protected static EventBus _eventBus = null;

    public static final String EventDetails = "event_details";
    public static final String EventId = "event_id";
    public static final String EntityType = "entity_type";
    public static final String EntityUuid = "entity_uuid";
    public static final String EntityDetails = "entity_details";

    @Inject EventDao eventDao;
    @Inject DomainDao domainDao;
    @Inject AccountDao accountDao;
    @Inject UserDao userDao;

    public ActionEventUtils() {
    }
    
    @PostConstruct
    void init() {
    	_eventDao = eventDao;
        _domainDao = domainDao;
    	_accountDao = accountDao;
    	_userDao = userDao;
    }

    public static Long onActionEvent(Long userId, Long accountId, Long domainId, String type, String description) {

        publishOnEventBus(userId, accountId, EventCategory.ACTION_EVENT.getName(),
                type, com.cloud.event.Event.State.Completed, description);

        Event event = persistActionEvent(userId, accountId, domainId, null, type, Event.State.Completed,
                description, null);

        return event.getId();
    }

    /*
     * Save event after scheduling an async job
     */
    public static Long onScheduledActionEvent(Long userId, Long accountId, String type, String description,
                                              long startEventId) {

        publishOnEventBus(userId, accountId, EventCategory.ACTION_EVENT.getName(), type,
                com.cloud.event.Event.State.Scheduled, description);

        Event event = persistActionEvent(userId, accountId, null, null, type, Event.State.Scheduled,
                description, startEventId);

        return event.getId();
    }
    
    public static void startNestedActionEvent(String eventType, String eventDescription) {
        CallContext.setActionEventInfo(eventType, eventDescription);
        onStartedActionEventFromContext(eventType, eventDescription);
    }

    public static void onStartedActionEventFromContext(String eventType, String eventDescription) {
        CallContext ctx = CallContext.current();
        long userId = ctx.getCallingUserId();
        long accountId = ctx.getCallingAccountId();
        long startEventId = ctx.getStartEventId();
        
        if ( ! eventType.equals("") )
            ActionEventUtils.onStartedActionEvent(userId, accountId, eventType, eventDescription, startEventId);
    }
    
    /*
     * Save event after starting execution of an async job
     */
    public static Long onStartedActionEvent(Long userId, Long accountId, String type, String description,
                                            long startEventId) {

        publishOnEventBus(userId, accountId, EventCategory.ACTION_EVENT.getName(), type,
                com.cloud.event.Event.State.Started, description);

        Event event = persistActionEvent(userId, accountId, null, null, type, Event.State.Started,
                description, startEventId);
        return event.getId();
    }

    public static Long onCompletedActionEvent(Long userId, Long accountId, String level, String type,
                                              String description, long startEventId) {

        description = addDescription(EventCategory.ACTION_EVENT.getName(), type, description);

        publishOnEventBus(userId, accountId, EventCategory.ACTION_EVENT.getName(), type,
                com.cloud.event.Event.State.Completed, description);

        Event event = persistActionEvent(userId, accountId, null, level, type, Event.State.Completed,
                description, startEventId);

        return event.getId();
    }

    public static Long onCreatedActionEvent(Long userId, Long accountId, String level, String type, String description) {

        publishOnEventBus(userId, accountId, EventCategory.ACTION_EVENT.getName(), type,
                com.cloud.event.Event.State.Created, description);

        Event event = persistActionEvent(userId, accountId, null, level, type, Event.State.Created, description, null);

        return event.getId();
    }

    private static Event persistActionEvent(Long userId, Long accountId, Long domainId, String level, String type,
                                           Event.State state, String description, Long startEventId) {
        EventVO event = new EventVO();
        event.setUserId(userId);
        event.setAccountId(accountId);
        event.setType(type);
        event.setState(state);
        event.setDescription(description);
        if (domainId != null) {
            event.setDomainId(domainId);
        } else {
            event.setDomainId(getDomainId(accountId));
        }
        if (level != null && !level.isEmpty()) {
            event.setLevel(level);
        }
        if (startEventId != null) {
            event.setStartId(startEventId);
        }
        event = _eventDao.persist(event);
        return event;
    }

    private static void publishOnEventBus(long userId, long accountId, String eventCategory,
                                          String eventType, Event.State state, String description) {
        try {
            _eventBus = ComponentContext.getComponent(EventBus.class);
        } catch(NoSuchBeanDefinitionException nbe) {
            return; // no provider is configured to provide events bus, so just return
        }

        // get the entity details for which ActionEvent is generated
        String entityType = null;
        String entityUuid = null;
        String oldEntityName = null;
        CallContext context = CallContext.current();
        if (context != null) {
            Class entityKey = getEntityKey(eventType);
            if (entityKey != null) {
                entityUuid = (String)context.getContextParameter(entityKey);
                if (entityUuid != null) {
                    entityType = entityKey.getName();
                    oldEntityName = (String)context.getContextParameter(entityUuid);
                }
            }
            else {
                entityType = (String)context.getContextParameter(EntityType);
                entityUuid = (String)context.getContextParameter(EntityUuid);
            }
        }

        org.apache.cloudstack.framework.events.Event event = new org.apache.cloudstack.framework.events.Event(
                ManagementServer.Name,
                eventCategory,
                eventType,
                EventTypes.getEntityForEvent(eventType), null);

        Map<String, String> eventDescription = new HashMap<String, String>();
        Account account = _accountDao.findById(accountId);
        User user = _userDao.findById(userId);
        // if account has been deleted, this might be called during cleanup of resources and results in null pointer
        if (account == null)
            return;
        if (user == null)
            return;
        eventDescription.put("user", user.getUuid());
        eventDescription.put("account", account.getUuid());
        eventDescription.put("event", eventType);
        eventDescription.put("status", state.toString());
        eventDescription.put("entity", entityType);
        eventDescription.put("entityuuid", entityUuid);
        eventDescription.put("description", description);
        eventDescription.put("oldentityname", oldEntityName);

        String eventDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(new Date());
        eventDescription.put("eventDateTime", eventDate);

        event.setDescription(eventDescription);

        try {
            _eventBus.publish(event);
        } catch (EventBusException e) {
            s_logger.warn("Failed to publish action event on the the event bus.");
        }
    }

    private static long getDomainId(long accountId){
        AccountVO account = _accountDao.findByIdIncludingRemoved(accountId);
        if (account == null) {
            s_logger.error("Failed to find account(including removed ones) by id '" + accountId + "'");
            return 0;
        }
        return account.getDomainId();
    }

    private static Class getEntityKey(String eventType)
    {
        if (eventType.startsWith("DOMAIN."))
        {
            return Domain.class;
        }
        else if (eventType.startsWith("ACCOUNT."))
        {
            return Account.class;
        }
        else if (eventType.startsWith("USER."))
        {
            return User.class;
        }

        return null;
    }

    private static String addDescription(String eventCategory, String eventType, String description)
    {
        if (!eventCategory.equals("ActionEvent"))   return description;
        if (!eventType.endsWith(".CREATE") && !eventType.endsWith(".DELETE") && !eventType.endsWith(".UPDATE"))    return description;

        Class entityKey = getEntityKey(eventType);
        if (entityKey == null)  return description;

        CallContext context = CallContext.current();
        String entityUuid = (String)context.getContextParameter(entityKey);
        if (entityUuid == null) return description;

        s_logger.debug("description before addition : " + description);

        String newEntityName = "";

        if (eventType.startsWith("DOMAIN."))
        {
            Domain domain = _domainDao.findByUuidIncludingRemoved(entityUuid);
            description += ", Domain Path:" + domain.getPath();
            newEntityName = domain.getName();
        }
        else if (eventType.startsWith("ACCOUNT."))
        {
            Account account = _accountDao.findByUuidIncludingRemoved(entityUuid);
            Domain domain = _domainDao.findById(account.getDomainId());
            if (eventType.endsWith(".CREATE"))
            {
                description += ", Domain Path:" + domain.getPath();
            }
            else
            {
                description += ", Account Name:" + account.getAccountName() + ", Domain Path:" + domain.getPath();
            }
            newEntityName = account.getAccountName();
        }
        else if (eventType.startsWith("USER."))
        {
            User user = _userDao.findByUuidIncludingRemoved(entityUuid);
            Account account = _accountDao.findById(user.getAccountId());
            Domain domain = _domainDao.findById(account.getDomainId());
            if (eventType.endsWith(".CREATE"))
            {
                description += ", Account Name:" + account.getAccountName() + ", Domain Path:" + domain.getPath();
            }
            else
            {
                description += ", User Name:" + user.getUsername() + ", Account Name:" + account.getAccountName() + ", Domain Path:" + domain.getPath();
            }
            newEntityName = user.getUsername();
        }

        if (eventType.endsWith(".UPDATE"))
        {
            String oldEntityName = (String)context.getContextParameter(entityUuid);
            description += ", Old Entity Name:" + oldEntityName + ", New Entity Name:" + newEntityName;
        }

        s_logger.debug("description after addition : " + description);

        return description;
    }
}