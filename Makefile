CXXFLAGS=-Wall -Wextra -Werror -O3 -DNDEBUG
#~ CXXFLAGS=-Wall -Wextra -Werror -g

BINARIES=threadserver epollserver
all: $(BINARIES)

threadserver : LDFLAGS += -lpthread
threadserver: threadserver.cc buffer.cc fifobuffer.cc

epollserver: epollserver.cc fifobuffer.cc

clean:
	$(RM) $(BINARIES) *.pyc
