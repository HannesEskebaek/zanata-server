/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.rest.service;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang.StringUtils;
import org.codehaus.enunciate.jaxrs.TypeHint;
import org.jboss.resteasy.annotations.providers.jaxb.Wrapped;
import org.jboss.resteasy.util.GenericType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Transactional;
import org.jboss.seam.annotations.security.Restrict;
import org.jboss.seam.log.Log;
import org.jboss.seam.log.Logging;
import org.jboss.seam.security.Identity;
import org.zanata.ApplicationConfiguration;
import org.zanata.common.ContentState;
import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.common.Namespaces;
import org.zanata.dao.DocumentDAO;
import org.zanata.dao.PersonDAO;
import org.zanata.dao.ProjectDAO;
import org.zanata.dao.ProjectIterationDAO;
import org.zanata.dao.TextFlowDAO;
import org.zanata.dao.TextFlowTargetDAO;
import org.zanata.dao.TextFlowTargetHistoryDAO;
import org.zanata.exception.ZanataServiceException;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.rest.NoSuchEntityException;
import org.zanata.rest.ReadOnlyEntityException;
import org.zanata.rest.dto.extensions.gettext.PoHeader;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.ResourceMeta;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.zanata.security.BaseSecurityChecker;
import org.zanata.service.CopyTransService;
import org.zanata.service.LocaleService;

import com.google.common.collect.Sets;

@Name("translationResourcesService")
@Path(TranslationResourcesService.SERVICE_PATH)
@Produces( { MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
@Consumes( { MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML })
@Transactional
/**
 * This service allows clients to push and pull both source documents and translations.
 */
public class TranslationResourcesService extends BaseSecurityChecker implements TranslationResourcesResource
{

   // security actions
   private static final String ACTION_IMPORT_TEMPLATE = "import-template";
   private static final String ACTION_IMPORT_TRANSLATION = "import-translation";

   public static final String SERVICE_PATH = ProjectIterationService.SERVICE_PATH + "/r";

   public static final String EVENT_COPY_TRANS = "org.zanata.rest.service.copyTrans";
   
   /** Project Identifier. */
   @PathParam("projectSlug")
   private String projectSlug;

   /** Project Iteration identifier. */
   @PathParam("iterationSlug")
   private String iterationSlug;

   /** (This parameter is optional and is currently not used) */
   @HeaderParam("Content-Type")
   @Context
   private MediaType requestContentType;

   @Context
   private HttpHeaders headers;

   @Context
   private Request request;

   @Context
   private UriInfo uri;

   @In
   private ApplicationConfiguration applicationConfiguration;

   @In
   private ProjectIterationDAO projectIterationDAO;

   @In
   private ProjectDAO projectDAO;

   @In
   private DocumentDAO documentDAO;

   @In
   private TextFlowDAO textFlowDAO;

   @In
   private TextFlowTargetDAO textFlowTargetDAO;

   @In
   private ResourceUtils resourceUtils;

   @In
   private ETagUtils eTagUtils;

   @In
   private PersonDAO personDAO;

   @In
   private TextFlowTargetHistoryDAO textFlowTargetHistoryDAO;
   
   @In
   private CopyTransService copyTransServiceImpl;

   private final Log log = Logging.getLog(TranslationResourcesService.class);

   @In
   private LocaleService localeServiceImpl;


   public TranslationResourcesService()
   {
   }

   // TODO break up this class (too many responsibilities)

// @formatter:off
   public TranslationResourcesService(
      ApplicationConfiguration applicationConfiguration,
      ProjectIterationDAO projectIterationDAO,
      ProjectDAO projectDAO,
      DocumentDAO documentDAO,
      TextFlowDAO textFlowDAO,
      TextFlowTargetDAO textFlowTargetDAO,
      ResourceUtils resourceUtils,
      Identity identity,
      ETagUtils eTagUtils,
      PersonDAO personDAO,
      TextFlowTargetHistoryDAO textFlowTargetHistoryDAO,
      LocaleService localeService,
      CopyTransService copyTransService
   )
// @formatter:on
   {
      this.applicationConfiguration = applicationConfiguration;
      this.projectIterationDAO = projectIterationDAO;
      this.projectDAO = projectDAO;
      this.documentDAO = documentDAO;
      this.textFlowDAO = textFlowDAO;
      this.textFlowTargetDAO = textFlowTargetDAO;
      this.resourceUtils = resourceUtils;
      this.identity = identity;
      this.eTagUtils = eTagUtils;
      this.personDAO = personDAO;
      this.textFlowTargetHistoryDAO = textFlowTargetHistoryDAO;
      this.localeServiceImpl = localeService;
      this.copyTransServiceImpl = copyTransService;
   }

   /**
    * Returns header information for a Project's iteration translations.
    * 
    * @return The following response status codes will be returned from this operation:<br>
    * OK(200) - Response containing an "Etag" header for the requested project iteration translations.<br>
    * NOT FOUND(404) - If a project iteration could not be found for the given parameters.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.  
    */
   @Override
   @HEAD
   public Response head()
   {
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(false);
      validateExtensions();
      EntityTag etag = projectIterationDAO.getResourcesETag(hProjectIteration);
      ResponseBuilder response = request.evaluatePreconditions(etag);
      if (response != null)
      {
         return response.build();
      }
      return Response.ok().tag(etag).build();
   }

   /**
    * Retrieve the List of Documents (Resources) belonging to a Project iteration.
    * 
    * @param extensions The document extensions to fetch along with the documents (e.g. "gettext", "comment"). This parameter
    * allows multiple values e.g. "ext=gettext&ext=comment".
    * @return  The following response status codes will be returned from this operation:<br>
    * OK(200) - Response with a list of documents wrapped around a "resources" tag. <br>
    * NOT FOUND(404) - If a Project iteration could not be found with the given parameters.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @GET
   @Wrapped(element = "resources", namespace = Namespaces.ZANATA_API)
   @TypeHint(ResourceMeta.class)
   public Response get(@QueryParam("ext") Set<String> extensions)
   {
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(false);

      EntityTag etag = projectIterationDAO.getResourcesETag(hProjectIteration);

      ResponseBuilder response = request.evaluatePreconditions(etag);
      if (response != null)
      {
         return response.build();
      }

      List<ResourceMeta> resources = new ArrayList<ResourceMeta>();

      for (HDocument doc : hProjectIteration.getDocuments().values())
      {
         // TODO we shouldn't need this check
         if (!doc.isObsolete())
         {
            ResourceMeta resource = new ResourceMeta();
            resourceUtils.transferToAbstractResourceMeta(doc, resource);
            resources.add(resource);
         }
      }

      Type genericType = new GenericType<List<ResourceMeta>>()
      {
      }.getGenericType();
      Object entity = new GenericEntity<List<ResourceMeta>>(resources, genericType);
      return Response.ok(entity).tag(etag).build();
   }


   /**
    * Creates a new Document.
    * 
    * @param resource The document information.
    * @param extensions The document extensions to save with the new document (e.g. "gettext", "comment"). This parameter
    * allows multiple values e.g. "ext=gettext&ext=comment".
    * @param copytrans Boolean value that indicates whether reasonably close translations from other projects should be 
    * found to initially populate this document's translations.
    * @return The following response status codes will be returned from this operation:<br>
    * CREATED (201) - If the document was successfully created.<br>
    * CONFLICT(409) - If another document already exists with the same name, on the same project iteration.<br>
    * UNAUTHORIZED(401) - If the user does not have the proper permissions to perform this operation.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @POST
   @Restrict("#{translationResourcesService.checkPermission('import-template')}")
   public Response post(Resource resource, @QueryParam("ext") Set<String> extensions, @QueryParam("copyTrans") @DefaultValue("true") boolean copytrans)
   {
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(true);

      validateExtensions(PoHeader.ID, PotEntryHeader.ID);

      HDocument document = documentDAO.getByDocIdAndIteration(hProjectIteration, resource.getName());
      HLocale hLocale = validateSourceLocale(resource.getLang());
      int nextDocRev;
      if (document != null)
      {
         if (!document.isObsolete())
         {
            // updates must happen through PUT on the actual resource
            return Response.status(Status.CONFLICT).entity("A document with name " + resource.getName() + " already exists.").build();
         }
         // a deleted document is being created again
         nextDocRev = document.getRevision() + 1;
         document.setObsolete(false);
      }
      else
      {
         nextDocRev = 1;
         document = new HDocument(resource.getName(), resource.getContentType(), hLocale);
         document.setProjectIteration(hProjectIteration);
      }
      hProjectIteration.getDocuments().put(resource.getName(), document);
      
      resourceUtils.transferFromResource(resource, document, extensions, hLocale, nextDocRev);

      document = documentDAO.makePersistent(document);
      documentDAO.flush();
      
      if (copytrans && nextDocRev == 1)
      {
         copyClosestEquivalentTranslation(document);
      }
           
      EntityTag etag = eTagUtils.generateETagForDocument(hProjectIteration, document.getDocId(), extensions);

      return Response.created(URI.create("r/" + resourceUtils.encodeDocId(document.getDocId()))).tag(etag).build();
   }

   /**
    * Retrieves information for a Document.
    * 
    * @param idNoSlash The document identifier. Some document ids could have forward slashes ('/') in them which would
    * cause conflicts with the browser's own url interpreter. For this reason, the supplied id must have all its '/' 
    * characters replaced with commas (',').
    * @param extensions The document extensions to fetch along with the document (e.g. "gettext", "comment"). This parameter
    * allows multiple values e.g. "ext=gettext&ext=comment".
    * @return The following response status codes will be returned from this operation:<br>
    * OK(200) - Response with the document's information.<br> 
    * NOT FOUND(404) - If a document could not be found with the given parameters.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @GET
   @Path(RESOURCE_SLUG_TEMPLATE)
   @TypeHint(Resource.class)
   // /r/{id}
   public Response getResource(@PathParam("id") String idNoSlash, @QueryParam("ext") Set<String> extensions)
   {
      log.debug("start get resource");
      String id = URIHelper.convertFromDocumentURIId(idNoSlash);
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(false);

      validateExtensions(PoHeader.ID, PotEntryHeader.ID);

      final Set<String> extSet = new HashSet<String>(extensions);
      EntityTag etag = eTagUtils.generateETagForDocument(hProjectIteration, id, extSet);

      ResponseBuilder response = request.evaluatePreconditions(etag);
      if (response != null)
      {
         return response.build();
      }

      HDocument doc = documentDAO.getByDocIdAndIteration(hProjectIteration, id);

      if (doc == null || doc.isObsolete())
      {
         return Response.status(Status.NOT_FOUND).entity("document not found").build();
      }

      Resource entity = new Resource(doc.getDocId());
      log.debug("get resource details {0}", entity.toString());
      resourceUtils.transferToResource(doc, entity);

      for (HTextFlow htf : doc.getTextFlows())
      {
         TextFlow tf = new TextFlow(htf.getResId(), doc.getLocale().getLocaleId());
         resourceUtils.transferToTextFlow(htf, tf);
         resourceUtils.transferToTextFlowExtensions(htf, tf.getExtensions(true), extensions);
         entity.getTextFlows().add(tf);
      }

      // handle extensions
      resourceUtils.transferToResourceExtensions(doc, entity.getExtensions(true), extensions);
      log.debug("Get resource :{0}", entity.toString());
      return Response.ok().entity(entity).tag(etag).lastModified(doc.getLastChanged()).build();
   }

   private HLocale validateTargetLocale(LocaleId locale, String projectSlug, String iterationSlug)
   {
      HLocale hLocale;
      try
      {
         hLocale = localeServiceImpl.validateLocaleByProjectIteration(locale, projectSlug, iterationSlug);
         return hLocale;
      }
      catch (ZanataServiceException e)
      {
         throw new WebApplicationException(Response.status(Status.FORBIDDEN).entity(e.getMessage()).build());
      }
   }

   private HLocale validateSourceLocale(LocaleId locale)
   {
      try
      {
         return localeServiceImpl.validateSourceLocale(locale);
      }
      catch (ZanataServiceException e)
      {
         throw new WebApplicationException(Response.status(Status.FORBIDDEN).entity(e.getMessage()).build());
      }
   }
   
   /**
    * Creates or modifies a Document.
    * 
    * @param idNoSlash The document identifier. Some document ids could have forward slashes ('/') in them which would
    * cause conflicts with the browser's own url interpreter. For this reason, the supplied id must have all its '/' 
    * characters replaced with commas (',').
    * @param resource The document information.
    * @param extensions The document extensions to save with the document (e.g. "gettext", "comment"). This parameter
    * allows multiple values e.g. "ext=gettext&ext=comment".
    * @param copytrans Boolean value that indicates whether reasonably close translations from other projects should be 
    * found to initially populate this document's translations.
    * @return The following response status codes will be returned from this operation:<br>
    * CREATED(201) - If a new document was successfully created.<br>
    * OK(200) - If an already existing document was modified.<br>
    * NOT FOUND(404) - If a project or project iteration could not be found with the given parameters.<br>
    * FORBIDDEN(403) - If the user is not allowed to modify the project, project iteration or document. This might be
    * due to the project or iteration being in Read-Only mode.<br>
    * UNAUTHORIZED(401) - If the user does not have the proper permissions to perform this operation.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @PUT
   @Path(RESOURCE_SLUG_TEMPLATE)
   @Restrict("#{translationResourcesService.checkPermission('import-template')}")
   // /r/{id}
   public Response putResource(@PathParam("id") String idNoSlash, Resource resource, @QueryParam("ext") Set<String> extensions, @QueryParam("copyTrans") @DefaultValue("true") boolean copytrans)
   {
      log.debug("start put resource");
      String id = URIHelper.convertFromDocumentURIId(idNoSlash);
      ResponseBuilder response;
      EntityTag etag = null;
      boolean changed = false;
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(true);

      validateExtensions();

      log.debug("resource details: {0}", resource);
      
      HDocument document = documentDAO.getByDocIdAndIteration(hProjectIteration, id);
      HLocale hLocale = validateSourceLocale(resource.getLang());
      int nextDocRev;
      if (document == null)
      { // must be a create operation
         nextDocRev = 1;
         response = request.evaluatePreconditions();
         if (response != null)
         {
            return response.build();
         }
         changed = true;
         // TODO check that entity name matches id parameter
         document = new HDocument(resource.getName(), resource.getContentType(), hLocale);
         document.setProjectIteration(hProjectIteration);
         hProjectIteration.getDocuments().put(id, document);
         response = Response.created(uri.getAbsolutePath());
      }
      else if (document.isObsolete())
      { // must also be a create operation
         nextDocRev = document.getRevision() + 1;
         response = request.evaluatePreconditions();
         if (response != null)
         {
            return response.build();
         }
         changed = true;
         document.setObsolete(false);
         // not sure if this is needed
         hProjectIteration.getDocuments().put(id, document);
         response = Response.created(uri.getAbsolutePath());
      }
      else
      { // must be an update operation
         nextDocRev = document.getRevision() + 1;
         etag = eTagUtils.generateETagForDocument(hProjectIteration, id, extensions);
         response = request.evaluatePreconditions(etag);
         if (response != null)
         {
            return response.build();
         }

         response = Response.ok();
      }

      changed |= resourceUtils.transferFromResource(resource, document, extensions, hLocale, nextDocRev);

      if (changed)
      {
         document = documentDAO.makePersistent(document);
         documentDAO.flush();
         etag = eTagUtils.generateETagForDocument(hProjectIteration, id, extensions);
      }

      if (copytrans && nextDocRev == 1)
      {
         copyClosestEquivalentTranslation(document);
      }
      
      log.debug("put resource successfully");
      return response.tag(etag).build();
   }

   /**
    * Delete a Document. The system keeps the history of this document however.
    * 
    * @param idNoSlash The document identifier. Some document ids could have forward slashes ('/') in them which would
    * cause conflicts with the browser's own url interpreter. For this reason, the supplied id must have all its '/' 
    * characters replaced with commas (',').
    * @return The following response status codes will be returned from this operation:<br>
    * OK(200) - If The document was successfully deleted.<br>
    * NOT FOUND(404) - If a project or project iteration could not be found with the given parameters.<br>
    * FORBIDDEN(403) - If the user is not allowed to modify the project, project iteration or document. This might be
    * due to the project or iteration being in Read-Only mode.<br>
    * UNAUTHORIZED(401) - If the user does not have the proper permissions to perform this operation.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @DELETE
   @Path(RESOURCE_SLUG_TEMPLATE)
   @Restrict("#{translationResourcesService.checkPermission('import-template')}")
   // /r/{id}
   public Response deleteResource(@PathParam("id") String idNoSlash)
   {
      String id = URIHelper.convertFromDocumentURIId(idNoSlash);
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(true);

      EntityTag etag = eTagUtils.generateETagForDocument(hProjectIteration, id, new HashSet<String>());

      ResponseBuilder response = request.evaluatePreconditions(etag);
      if (response != null)
      {
         return response.build();
      }

      HDocument document = documentDAO.getByDocIdAndIteration(hProjectIteration, id);
      document.setObsolete(true);
      documentDAO.flush();
      return Response.ok().build();
   }

   /**
    * Retrieves meta-data information for a Document.
    * 
    * @param idNoSlash The document identifier. Some document ids could have forward slashes ('/') in them which would
    * cause conflicts with the browser's own url interpreter. For this reason, the supplied id must have all its '/' 
    * characters replaced with commas (',').
    * @param extensions The document extensions to retrieve with the document's meta-data (e.g. "gettext", "comment"). 
    * This parameter allows multiple values e.g. "ext=gettext&ext=comment".
    * @return The following response status codes will be returned from this operation:<br>
    * OK(200) - If the Document's meta-data was found. The data will be contained in the response.<br>
    * NOT FOUND(404) - If a project, project iteration or document could not be found with the given parameters.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @GET
   @Path(RESOURCE_SLUG_TEMPLATE + "/meta")
   @TypeHint(ResourceMeta.class)
   // /r/{id}/meta
   public Response getResourceMeta(@PathParam("id") String idNoSlash, @QueryParam("ext") Set<String> extensions)
   {
      log.debug("start to get resource meta");
      String id = URIHelper.convertFromDocumentURIId(idNoSlash);
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(false);

      EntityTag etag = eTagUtils.generateETagForDocument(hProjectIteration, id, extensions);

      ResponseBuilder response = request.evaluatePreconditions(etag);
      if (response != null)
      {
         return response.build();
      }

      HDocument doc = documentDAO.getByDocIdAndIteration(hProjectIteration, id);

      if (doc == null)
      {
         return Response.status(Status.NOT_FOUND).entity("document not found").build();
      }

      ResourceMeta entity = new ResourceMeta(doc.getDocId());
      resourceUtils.transferToAbstractResourceMeta(doc, entity);

      // transfer extensions
      resourceUtils.transferToResourceExtensions(doc, entity.getExtensions(true), extensions);

      log.debug("successfuly get resource meta: {0}" , entity);
      return Response.ok().entity(entity).tag(etag).build();
   }

   /**
    * Modifies an existing document's meta-data.
    * 
    * @param idNoSlash The document identifier. Some document ids could have forward slashes ('/') in them which would
    * cause conflicts with the browser's own url interpreter. For this reason, the supplied id must have all its '/' 
    * characters replaced with commas (',').
    * @param messageBody The document's meta-data.
    * @param extensions The document extensions to save with the document (e.g. "gettext", "comment"). This parameter
    * allows multiple values e.g. "ext=gettext&ext=comment".
    * @return The following response status codes will be returned from this operation:<br>
    * OK(200) - If the Document's meta-data was successfully modified.<br>
    * NOT FOUND(404) - If a document was not found using the given parameters.<br>
    * UNAUTHORIZED(401) - If the user does not have the proper permissions to perform this operation.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @PUT
   @Path(RESOURCE_SLUG_TEMPLATE + "/meta")
   @Restrict("#{translationResourcesService.checkPermission('import-template')}")
   // /r/{id}/meta
   public Response putResourceMeta(@PathParam("id") String idNoSlash, ResourceMeta messageBody, @QueryParam("ext") Set<String> extensions)
   {
      log.debug("start to put resource meta");
      String id = URIHelper.convertFromDocumentURIId(idNoSlash);
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(true);

      EntityTag etag = eTagUtils.generateETagForDocument(hProjectIteration, id, extensions);

      ResponseBuilder response = request.evaluatePreconditions(etag);
      if (response != null)
      {
         return response.build();
      }

      log.debug("pass evaluation");
      log.debug("put resource meta: {0}", messageBody);

      HDocument document = documentDAO.getByDocIdAndIteration(hProjectIteration, id);
      if (document == null)
      {
         return Response.status(Status.NOT_FOUND).build();
      }
      if (document.isObsolete())
      {
         return Response.status(Status.NOT_FOUND).build();
      }
      HLocale hLocale = validateTargetLocale(messageBody.getLang(), projectSlug, iterationSlug);
      boolean changed = resourceUtils.transferFromResourceMetadata(messageBody, document, extensions, hLocale, document.getRevision() + 1);

      if (changed)
      {
         documentDAO.flush();
         etag = eTagUtils.generateETagForDocument(hProjectIteration, id, extensions);
      }

      log.debug("put resource meta successfully");
      return Response.ok().tag(etag).lastModified(document.getLastChanged()).build();

   }

   /**
    * Retrieves a set of translations for a given locale.
    * 
    * @param idNoSlash The document identifier. Some document ids could have forward slashes ('/') in them which would
    * cause conflicts with the browser's own url interpreter. For this reason, the supplied id must have all its '/' 
    * characters replaced with commas (',').
    * @param locale The locale for which to get translations.
    * @param extensions The translation extensions to retrieve (e.g. "comment"). This parameter
    * allows multiple values.
    * @return The following response status codes will be returned from this operation:<br>
    * OK(200) - Successfully retrieved translations. The data will be contained in the response.<br>
    * NOT FOUND(404) - If a project, project iteration or document could not be found with the given parameters.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @GET
   @Path(RESOURCE_SLUG_TEMPLATE + "/translations/{locale}")
   @TypeHint(TranslationsResource.class)
   // /r/{id}/translations/{locale}
   public Response getTranslations(
         @PathParam("id") String idNoSlash,
         @PathParam("locale") LocaleId locale,
         @QueryParam("ext") Set<String> extensions,
         @QueryParam("skeletons") @DefaultValue("false") boolean skeletons
         )
   {
      log.debug("start to get translation");
      String id = URIHelper.convertFromDocumentURIId(idNoSlash);
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(false);

      validateExtensions();

      // TODO create valid etag
      EntityTag etag = eTagUtils.generateETagForDocument(hProjectIteration, id, extensions);

      ResponseBuilder response = request.evaluatePreconditions(etag);
      if (response != null)
      {
         return response.build();
      }

      HDocument document = documentDAO.getByDocIdAndIteration(hProjectIteration, id);
      if (document.isObsolete())
      {
         return Response.status(Status.NOT_FOUND).build();
      }

      HLocale hLocale = validateTargetLocale(locale, projectSlug, iterationSlug);
      TranslationsResource translationResource = new TranslationsResource();
      boolean foundData = resourceUtils.transferToTranslationsResource(
            translationResource, document, hLocale, extensions, 
            textFlowTargetDAO.findTranslations(document, hLocale));

      if (!foundData && !skeletons)
      {
         return Response.status(Status.NOT_FOUND).build();
      }

      // TODO lastChanged
      return Response.ok().entity(translationResource).tag(etag).build();
   }

   /**
    * Deletes a set of translations for a given locale. Also deletes any extensions recorded for the translations in
    * question. The system will keep history of the translations.
    * 
    * @param idNoSlash The document identifier. Some document ids could have forward slashes ('/') in them which would
    * cause conflicts with the browser's own url interpreter. For this reason, the supplied id must have all its '/' 
    * characters replaced with commas (',').
    * @param locale The locale for which to get translations.
    * @return The following response status codes will be returned from this operation:<br>
    * OK(200) - Successfully deleted the translations.<br>
    * NOT FOUND(404) - If a project, project iteration or document could not be found with the given parameters.
    * UNAUTHORIZED(401) - If the user does not have the proper permissions to perform this operation.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @DELETE
   @Path(RESOURCE_SLUG_TEMPLATE + "/translations/{locale}")
   @Restrict("#{translationResourcesService.checkPermission('import-translation')}")
   // /r/{id}/translations/{locale}
   public Response deleteTranslations(@PathParam("id") String idNoSlash, @PathParam("locale") LocaleId locale)
   {
      String id = URIHelper.convertFromDocumentURIId(idNoSlash);
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(true);

      // TODO find correct etag
      EntityTag etag = eTagUtils.generateETagForDocument(hProjectIteration, id, new HashSet<String>());

      ResponseBuilder response = request.evaluatePreconditions(etag);
      if (response != null)
      {
         return response.build();
      }

      HDocument document = documentDAO.getByDocIdAndIteration(hProjectIteration, id);
      if (document.isObsolete())
      {
         return Response.status(Status.NOT_FOUND).build();
      }
      List<HTextFlowTarget> targets = textFlowTargetDAO.findAllTranslations(document, locale);

      for (HTextFlowTarget target : targets)
      {
         target.clear();
      }

      // we also need to delete the extensions here
      document.getPoTargetHeaders().remove(locale);
      textFlowTargetDAO.flush();

      return Response.ok().build();

   }

   /**
    * Updates the translations for a document and a locale.
    * 
    * @param idNoSlash The document identifier. Some document ids could have forward slashes ('/') in them which would
    * cause conflicts with the browser's own url interpreter. For this reason, the supplied id must have all its '/' 
    * characters replaced with commas (',').
    * @param locale The locale for which to get translations.
    * @param messageBody The translations to modify.
    * @param extensions The translation extension types to modify (e.g. "comment"). This parameter
    * allows multiple values.
    * @param merge Indicates how to deal with existing translations (valid options: 'auto', 'import'). Import will 
    * overwrite all current values with the values being pushed (even empty ones), while Auto will check the history 
    * of your translations and will not overwrite any translations for which it detects a previous value is being pushed.
    * @return The following response status codes will be returned from this operation:<br>
    * OK(200) - Translations were successfully updated.<br>
    * NOT FOUND(404) - If a project, project iteration or document could not be found with the given parameters.<br>
    * UNAUTHORIZED(401) - If the user does not have the proper permissions to perform this operation.<br>
    * BAD REQUEST(400) - If there are problems with the parameters passed. i.e. Merge type is not one of the accepted 
    * types. This response should have a content message indicating a reason.<br>
    * INTERNAL SERVER ERROR(500) - If there is an unexpected error in the server while performing this operation.
    */
   @Override
   @PUT
   @Path(RESOURCE_SLUG_TEMPLATE + "/translations/{locale}")
   @Restrict("#{translationResourcesService.checkPermission('import-translation')}")
   // /r/{id}/translations/{locale}
   public Response putTranslations(@PathParam("id") String idNoSlash, @PathParam("locale") LocaleId locale, TranslationsResource messageBody, @QueryParam("ext") Set<String> extensions, @QueryParam("merge") @DefaultValue("auto") String merge)
   {
      log.debug("start put translations");
      MergeType mergeType;
      try
      {
         mergeType = MergeType.valueOf(merge.toUpperCase());
      }
      catch (Exception e)
      {
         return Response.status(Status.BAD_REQUEST).entity("bad merge type "+merge).build();
      }
      String id = URIHelper.convertFromDocumentURIId(idNoSlash);
      HProjectIteration hProjectIteration = retrieveAndCheckIteration(true);

      validateExtensions();

      // TODO create valid etag
      EntityTag etag = eTagUtils.generateETagForDocument(hProjectIteration, id, new HashSet<String>());

      ResponseBuilder response = request.evaluatePreconditions(etag);
      if (response != null)
      {
         return response.build();
      }

      log.debug("pass evaluate");
      HDocument document = documentDAO.getByDocIdAndIteration(hProjectIteration, id);
      if (document.isObsolete())
      {
         return Response.status(Status.NOT_FOUND).build();
      }


      log.debug("start put translations entity:{0}" , messageBody);

      boolean changed = false;

      HLocale hLocale = validateTargetLocale(locale, projectSlug, iterationSlug);
      // handle extensions
      changed |= resourceUtils.transferFromTranslationsResourceExtensions(messageBody.getExtensions(true), document, extensions, hLocale, mergeType);

      List<HPerson> newPeople = new ArrayList<HPerson>();
      // NB: removedTargets only applies for MergeType.IMPORT
      Collection<HTextFlowTarget> removedTargets = new HashSet<HTextFlowTarget>();
      Collection<String> unknownResIds = new LinkedHashSet<String>();

      if (mergeType == MergeType.IMPORT)
      {
         for (HTextFlow textFlow : document.getTextFlows())
         {
            HTextFlowTarget hTarget = textFlow.getTargets().get(hLocale);
            if (hTarget != null)
            {
               removedTargets.add(hTarget);
            }
         }
      }

      for (TextFlowTarget incomingTarget : messageBody.getTextFlowTargets())
      {
         String resId = incomingTarget.getResId();
         HTextFlow textFlow = textFlowDAO.getById(document, resId);
         if (textFlow == null)
         {
            // return warning for unknown resId to REST client
            unknownResIds.add(resId);
            log.warn("skipping TextFlowTarget with unknown resId: {0}", resId);
            continue;
         }
         else
         {
            if (incomingTarget.getContent().isEmpty() && incomingTarget.getState() != ContentState.New)
            {
               return Response.status(Status.BAD_REQUEST).entity("empty TextFlowTarget " + incomingTarget.getResId() + " must have ContentState New").build();
            }
            if (incomingTarget.getState() == ContentState.New && !incomingTarget.getContent().isEmpty())
            {
               return Response.status(Status.BAD_REQUEST).entity("ContentState New is illegal for non-empty TextFlowTarget " + incomingTarget.getResId()).build();
            }

            HTextFlowTarget hTarget = textFlow.getTargets().get(hLocale);
            boolean targetChanged = false;
            if (hTarget == null)
            {
               targetChanged = true;
               log.debug("locale: {0}", locale);
               hTarget = new HTextFlowTarget(textFlow, hLocale);
               hTarget.setVersionNum(0); // incremented when content is set
               textFlow.getTargets().put(hLocale, hTarget);
               targetChanged |= resourceUtils.transferFromTextFlowTarget(incomingTarget, hTarget);
               targetChanged |= resourceUtils.transferFromTextFlowTargetExtensions(incomingTarget.getExtensions(true), hTarget, extensions);
            }
            else
            {
               switch (mergeType)
               {
               case AUTO:
                  if (incomingTarget.getState() != ContentState.New)
                  {
                     if (hTarget.getState() == ContentState.New)
                     {
                        targetChanged |= resourceUtils.transferFromTextFlowTarget(incomingTarget, hTarget);
                        targetChanged |= resourceUtils.transferFromTextFlowTargetExtensions(incomingTarget.getExtensions(true), hTarget, extensions);
                     }
                     else if (incomingTarget.getState() == ContentState.Approved)
                     {
                        List<String> incomingContents = incomingTarget.getContents();
                        boolean oldContent = textFlowTargetHistoryDAO.findContentInHistory(hTarget, incomingContents);
                        if (!oldContent)
                        {
                           targetChanged |= resourceUtils.transferFromTextFlowTarget(incomingTarget, hTarget);
                           targetChanged |= resourceUtils.transferFromTextFlowTargetExtensions(incomingTarget.getExtensions(true), hTarget, extensions);
                        }
                     }
                     else
                     {
                        // incomingTarget state = NeedReview
                        // hTarget state != New

                        // we don't overwrite the server's NeedReview or Approved value (business rule)
                     }
                  }
                  break;

               case IMPORT:
                  removedTargets.remove(hTarget);
                  targetChanged |= resourceUtils.transferFromTextFlowTarget(incomingTarget, hTarget);
                  targetChanged |= resourceUtils.transferFromTextFlowTargetExtensions(incomingTarget.getExtensions(true), hTarget, extensions);
                  break;

               default:
                  return Response.status(Status.INTERNAL_SERVER_ERROR).entity("unhandled merge type " + mergeType).build();
               }
            }

            // update translation information if applicable
            if (targetChanged)
            {
               changed = true;
               if (incomingTarget.getTranslator() != null)
               {
                  String email = incomingTarget.getTranslator().getEmail();
                  HPerson hPerson = personDAO.findByEmail(email);
                  if (hPerson == null)
                  {
                     hPerson = new HPerson();
                     hPerson.setEmail(email);
                     hPerson.setName(incomingTarget.getTranslator().getName());
                     newPeople.add(hPerson);
                  }
                  hTarget.setLastModifiedBy(hPerson);
               }
               else
               {
                  hTarget.setLastModifiedBy(null);
               }
               textFlowTargetDAO.makePersistent(hTarget);
            }
            incomingTarget = null;
         }
      }
      if (changed || !removedTargets.isEmpty())
      {
         for (HPerson person : newPeople)
         {
            personDAO.makePersistent(person);
         }
         personDAO.flush();

         for (HTextFlowTarget target : removedTargets)
         {
            target.clear();
         }
         textFlowTargetDAO.flush();

         documentDAO.flush();

         // TODO create valid etag
         etag = eTagUtils.generateETagForDocument(hProjectIteration, id, extensions);
      }
      log.debug("successful put translation");
      // TODO lastChanged
      if (unknownResIds.isEmpty())
         return Response.ok().tag(etag).build();
      else
         return Response.ok("warning: unknown resIds: " + unknownResIds).tag(etag).build();
   }

   private HProjectIteration retrieveAndCheckIteration(boolean writeOperation)
   {
      HProjectIteration hProjectIteration = projectIterationDAO.getBySlug(projectSlug, iterationSlug);
      HProject hProject = projectDAO.getBySlug(projectSlug);

      if (hProjectIteration == null)
      {
         throw new NoSuchEntityException("Project Iteration '" + projectSlug + ":" + iterationSlug + "' not found.");
      }
      else if (hProjectIteration.getStatus().equals(EntityStatus.OBSOLETE) || hProject.getStatus().equals(EntityStatus.OBSOLETE))
      {
         throw new NoSuchEntityException("Project Iteration '" + projectSlug + ":" + iterationSlug + "' not found.");
      }
      else if (writeOperation)
      {
         if (hProjectIteration.getStatus().equals(EntityStatus.READONLY) || hProject.getStatus().equals(EntityStatus.READONLY))
         {
            throw new ReadOnlyEntityException("Project Iteration '" + projectSlug + ":" + iterationSlug + "' is read-only.");
         }
         else
         {
            return hProjectIteration;
         }
      }
      else
      {
         return hProjectIteration;
      }
   }

   /**
    * Ensures that any extensions sent with the current query are valid for this
    * context.
    * 
    * @param validExt any number of extension ids that are valid for the context
    * @throws WebApplicationException if any unsupported extensions are present
    */
   private void validateExtensions(String... validExt) throws WebApplicationException
   {
      Set<String> validExtensions = Sets.newHashSet(validExt);
      Set<String> invalidExtensions = null;
      for (String ext : validExt)
      {
         if (!validExtensions.contains(ext))
         {
            if (invalidExtensions == null)
            {
               invalidExtensions = new HashSet<String>();
            }
            invalidExtensions.add(ext);
         }
      }

      if (invalidExtensions != null)
      {
         throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity("Unsupported Extensions within this context: " + StringUtils.join(invalidExtensions, ",")).build());
      }
   }
   

   public void copyClosestEquivalentTranslation(HDocument document)
   {
      if (applicationConfiguration.getEnableCopyTrans())
      {
         copyTransServiceImpl.copyTransForDocument(document);
      }
   }
   
   @Override
   public Object getSecuredEntity()
   {
      return retrieveAndCheckIteration(false);
   }

}
