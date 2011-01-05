package org.pih.warehouse.product;

import org.junit.runner.Request;
import org.pih.warehouse.product.Category;
import org.pih.warehouse.product.Product;
import grails.converters.JSON;

import au.com.bytecode.opencsv.CSVReader;

class ProductController {

    static allowedMethods = [save: "POST", update: "POST", delete: "POST"];    
	def inventoryService;
	
	
    def index = {
        redirect(action: "list", params: params)
    }

	//params.max = Math.min(params.max ? params.int('max') : 10, 100)
    //    [eventTypeInstanceList: EventType.list(params), eventTypeInstanceTotal: EventType.count()]

	
    def browse = { 
		params.max = Math.min(params.max ? params.int('max') : 10, 100);
		
    	// Get selected 
		def selectedCategory = Category.get(params.categoryId);		
    	def selectedAttribute = Attribute.get(params.attributeId)	
		
		def rootCategory = Category.findByName("ROOT");
		selectedCategory = (selectedCategory)?:rootCategory;

    	// Condition types	
    	def allAttributes = Attribute.getAll();
    	
		// Root categories
		def categoryCriteria = Category.createCriteria();		
		def allCategories = categoryCriteria.list { 
			isNull("parentCategory")
		}

				
		// Search for Products matching criteria 
		/*
		def results = Product.createCriteria().list(max:params.max, offset: params.offset ?: 0) {
            and{
          		if(params.categoryId){
					//or { 
					//	eq ("category.id", Long.parseLong(params.categoryId))
	          		//	categories { 
	          		//		eq("id", Long.parseLong(params.categoryId))
	          		//	}
					//}
                } 
				if (params.nameContains) {  
					or { 
						ilike("name", "%" + params.nameContains + "%")
						ilike("inn", "%" + params.nameContains + "%")
						ilike("brandName", "%" + params.nameContains + "%")
					}
				}
            }				
		}		
		*/
		
		def products = inventoryService.getProductsByCategory(selectedCategory, params);
		def productsByCategory = products.groupBy { it.category } 
				
		
		
		render(view:'browse', model:[productInstanceList : products, 
    	                             productInstanceTotal: products.totalCount, 
									 productsByCategory : productsByCategory,
									 rootCategory : rootCategory,
									 categoryInstance: selectedCategory,
    	                             categories : allCategories, selectedCategory : selectedCategory,
    	                             attributes : allAttributes, selectedAttribute : selectedAttribute ])
	}
    
    
    def list = {
        params.max = Math.min(params.max ? params.int('max') : 10, 100)
        [productInstanceList: Product.list(params), productInstanceTotal: Product.count()]
    }
	
	def create = { 
		def productInstance = new Product(params)
		render(view: "edit", model: [productInstance : productInstance, rootCategory: Category.findByName("ROOT")])
	}

	
    def save = {
        //def productInstance = new Product(params)
		log.info "save called with params " + params
		log.info "type = " + params.type;
		
		def productInstance = new Product(params);	
		productInstance?.categories?.clear();
		println "size: " + productInstance?.categories?.size()
		params.each {
			println ("category: " + it.key +  " starts with category_ " + it.key.startsWith("category_"))			
			if (it.key.startsWith("category_")) {
				def category = Category.get((it.key - "category_") as Integer);
				log.info "adding " + category?.name
				productInstance.addToCategories(category)
			}
		  }

		if (productInstance.save(flush: true)) {
            flash.message = "${message(code: 'default.created.message', args: [message(code: 'product.label', default: 'Product'), productInstance.name])}"
            redirect(action: "browse", params:params)
        }
        else {
            render(view: "edit", model: [productInstance: productInstance])
        }
    }

    def show = {
        def productInstance = Product.get(params.id)
        if (!productInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'product.label', default: 'Product'), params.id])}"
            redirect(action: "browse")
        }
        else {
            [productInstance: productInstance]
        }
    }

    def edit = {
        def productInstance = Product.get(params.id)
        if (!productInstance) {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'product.label', default: 'Product'), params.id])}"
            redirect(action: "browse")
        }
        else {
            return [productInstance: productInstance, rootCategory: Category.findByName("ROOT")]
        }
    }
	

    def update = {
		
		log.info "update called with params " + params
        def productInstance = Product.get(params.id)
		log.info " " + productInstance.class.simpleName
		
        if (productInstance) {
            if (params.version) {
                def version = params.version.toLong()
                if (productInstance.version > version) {                    
                    productInstance.errors.rejectValue("version", "default.optimistic.locking.failure", [message(code: 'product.label', default: 'Product')] as Object[], "Another user has updated this Product while you were editing")
                    render(view: "edit", model: [productInstance: productInstance])
                    return
                }
            }
            productInstance.properties = params
			
			productInstance?.categories?.clear();		
			params.each {
				println ("category: " + it.key +  " starts with category_ " + it.key.startsWith("category_"))
				
				if (it.key.startsWith("category_")) {
					def category = Category.get((it.key - "category_") as Integer);
					log.info "adding " + category?.name
					productInstance.addToCategories(category)
				}
			  }
			
            if (!productInstance.hasErrors() && productInstance.save(flush: true)) {
                flash.message = "${message(code: 'default.updated.message', args: [message(code: 'product.label', default: 'Product'), productInstance.name])}"
                redirect(action: "browse", params:params)
            }
            else {
                render(view: "edit", model: [productInstance: productInstance])
            }
        }
        else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'product.label', default: 'Product'), params.id])}"
            redirect(action: "browse", params:params)
        }
    }

    def delete = {
        def productInstance = Product.get(params.id)
        if (productInstance) {
            try {
                productInstance.delete(flush: true)
                flash.message = "${message(code: 'default.deleted.message', args: [message(code: 'product.label', default: 'Product'), params.id])}"
                redirect(action: "list")
            }
            catch (org.springframework.dao.DataIntegrityViolationException e) {
                flash.message = "${message(code: 'default.not.deleted.message', args: [message(code: 'product.label', default: 'Product'), params.id])}"
                redirect(action: "show", id: params.id)
            }
        }
        else {
            flash.message = "${message(code: 'default.not.found.message', args: [message(code: 'product.label', default: 'Product'), params.id])}"
            redirect(action: "browse")
        }
    }
	
	/**
	 * 
	 */
	def importDependencies = { 		
		/*
		if (session.dosageForms) {
			session.dosageForms.unique().each() {
				new DosageForm(code: it, name: it).save(flush:true)
			}
			session.dosageForms = null
		}*/
				
		redirect(controller: "product", action: "importProducts")	
	}
	
	/**
	* Import contents of CSV file
	*/
   def importProducts = {
	   
	   if ("GET".equals(request.getMethod())) {
		   render(view: "uploadProducts");
	   }
	   else if ("POST".equals(request.getMethod())) {
		   log.info "POST request"
		   if (!session.products) {
			   log.info "GET request"
			   
			   flash.message = "Please upload a CSV file with valid products";
		   }
		   else {			   
			   
			   if (!session.dosageForms && !session.productTypes) { 
				   session.products.each() {					   
					   def productInstance = new Product(	name: it.name, 
															productCode: it.productCode);						
						productInstance.save(failOnError:true);
					};
				   // import
				   flash.message = "Products imported successfully"
				   redirect(controller: "product", action: "browse")
			   }			   								   
			   else { 
				   flash.message = "Please import dependencies first"
				   redirect(controller: "product", action: "importProducts")				   
			   }
		   }
	   }
   }


	   
   /**
	* Upload and process CSV file
	*/
   def uploadProducts = {
	   
	   if ("POST".equals(request.getMethod())) {
		   
		   
		   def uploadFile = request.getFile('csvFile');
		   
		   // file must be less than 1MB
		   if (!uploadFile?.empty) {
			   File csvFile = new File("/tmp/warehouse/products/import/" + uploadFile.originalFilename);
			   csvFile.mkdirs()
			   
			   uploadFile.transferTo(csvFile);
			   
			   
			   //def sql = Sql.newInstance("jdbc:mysql://localhost:3306/mydb", "user", "pswd", "com.mysql.jdbc.Driver")
			   //def people = sql.dataSet("PERSON")
			   List<Product> products = new ArrayList<Product>();
			   
			   /*
			   csvFile.splitEachLine(",") { fields ->
				   log.info("field0: " + fields[0])
				   log.info("field1: " + fields[1])
				   log.info("field2: " + fields[2])
				   
				   products.add(
					   new ProductCommand(
						   id: fields[0],
						   ean: fields[1],
						   name: fields[2],
						   description: fields[3],
						   productType: fields[4]));
			   }*/

			   
				// Process CSV file
				def columns;
				def productTypes = new HashSet<String>();
				def categories = []
				def unitOfMeasures = []
				def dosageForms = new HashSet<String>();
				
				
				CSVReader csvReader = new CSVReader(new FileReader(csvFile.getAbsolutePath()), (char) ',', (char) '\"', 1);
				while ((columns = csvReader.readNext()) != null) {
					
					// 0 => type
					// 1 => productType
					// 2 => productCode
					// 3 => name
					// 4 => frenchName
					// 5 => dosageStrength
					// 6 => unitOfMeasure
					// 7 => dosageForm
					
					def productInstance = new Product();
					productInstance.productCode = columns[2]
					productInstance.name = columns[3]
					productInstance.frenchName = columns[4]
					productInstance.dosageStrength = columns[5]
					productInstance.dosageUnit = columns[6]
					
					/*
					def productTypeValue = columns[1];
					if (productTypeValue && !productTypeValue.equals("") && !productTypeValue.equals("null")) { 
						def productType = ProductType.findByName(productTypeValue);
						if (!productType) { 
							productInstance.productType = new ProductType(name: productTypeValue, code: productTypeValue);						
							productTypes.add(productTypeValue);
						}else { 
							productInstance.productType = productType
						}
					}					
					
					def dosageFormValue = columns[7];
					if (dosageFormValue && !dosageFormValue.equals("") && !dosageFormValue.equals("null")) { 					
						def dosageForm = DosageForm.findByName(dosageFormValue);					
						if (!dosageForm) { 
							productInstance.dosageForm = new DosageForm(name: dosageFormValue, code: dosageFormValue)
							dosageForms.add(dosageFormValue);
						}
						else { 
							productInstance.dosageForm = dosageForm
						}
					}
					*/
					
											
					products.add(productInstance);

			   }
			   
			   
			   
			   session.products = products;
			   //session.categories = categories;
			   session.productTypes = productTypes;
			   session.dosageForms = dosageForms;
			   
			   if (dosageForms || productTypes) { 
				   flash.message = "Please import all dependencies first"
				   render(view: "importProducts", model: [productTypes: productTypes, dosageForms: dosageForms]);
			   }
			   else { 
				   render(view: "importProducts", model: [products:products]);				   
			   }
		   }
		   else {
			   flash.message = "Please upload a non-empty CSV file";
		   }
	   }
   }
	
	
}



class ProductCommand {
	String id
	String ean
	String name
	String description
	String productType
	
	static constraints = {
	   ean(nullable: true, blank: false)
	   name(nullable: true, blank: false)
	   description(nullable:true, blank:false)
	   productType(nullable:true, blank:false)
	}
 }
