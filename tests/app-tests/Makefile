#
#   Makefile for tests/app_tests subdirectory
#   Note: This is the 'development' makefile for Bruce's machine
#   It is not part of the distribution, which is renamed
#   from Makefile.dist.
#
#   The purpose of this makefile is to bring fresh copies of example
#   models from various directories on Bruce's machine into the
#   test_models directory so they are available for the app-tests in
#   each tool.


PROJECTS  = ${HOME}/Documents/Projects
DAVE_DIR  = ${PROJECTS}/DAVE
MODELS_DIR= ${DAVE_DIR}/Tech/Models
SCHEMA_DIR= ${DAVE_DIR}/Schema
EXAMPLES  = ${SCHEMA_DIR}/Examples
F16_DIR   = ${MODELS_DIR}/F-16
HL20_DIR  = ${PROJECTS}/HL-20/Sim_models/DAVE

VPATH = ${EXAMPLES}:${F16_DIR}:${HL20_DIR}

include Makefile.dist

# this makes sure any necessary directories are added

%/.d:
	mkdir -p $(@D)
	@touch $@

test_models/%.dml : %.dml test_models/.d
	cp $< $@

copy_models: test_models $(addprefix test_models/,${MODEL_LIST})


distclean:
	-rm -rf test_models

.PHONY: copy_models distclean

.PRECIOUS: %/.d

