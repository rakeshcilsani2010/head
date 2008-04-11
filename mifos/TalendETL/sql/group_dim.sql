create table group_dim (
  group_id int(15) not null auto_increment,
  global_cust_num varchar(100) not null default '',
  display_name varchar(200) default null,
  center_id smallint(5) default null,
  loan_officer_id smallint(5) default null,
  external_id varchar(50) default null,
  created_date date default null,
  updated_date date default null,
  mfi_joining_date date default null,
  customer_activation_date date default null,
  created_by smallint(5) default null,
  updated_by smallint(5) default null,
  primary key  (group_id),
    unique key global_cust_num (global_cust_num),
  constraint group_dim_ibfk_2 foreign key (center_id) references center_dim (center_id) on delete no action on update no action
) engine=innodb default charset=utf8;